/**
 * CG Session Protocol client over WebSocket.
 *
 * Speaks the same CBOR-tagged protocol as the native TCP session client.
 * Each WebSocket binary message is a tagged CBOR map matching ProtocolMessage.
 */
const Session = (() => {
    // CG protocol CBOR tags (from Canonical.CgTag)
    const Tag = {
        AUTH: 13,
        CONTEXT: 14,
        DISPATCH: 15,
        LOOKUP: 16,
        SUBSCRIBE: 17,
        EVENT: 18,
        STREAM: 19,
        HEARTBEAT: 20,
        ACK: 21,
        ERROR: 22
    };

    let ws = null;
    let requestCounter = 1;
    const pendingRequests = new Map();
    let eventHandler = null;
    let statusHandler = null;
    let sessionId = null;

    function connect(url) {
        return new Promise((resolve, reject) => {
            ws = new WebSocket(url);
            ws.binaryType = 'arraybuffer';

            ws.onopen = () => {
                setStatus('connected, awaiting challenge...');
            };

            ws.onmessage = (event) => {
                try {
                    const msg = CBOR.decode(event.data);
                    handleMessage(msg, resolve);
                } catch (e) {
                    console.error('Failed to decode message:', e);
                }
            };

            ws.onerror = (event) => {
                setStatus('connection error');
                reject(new Error('WebSocket error'));
            };

            ws.onclose = () => {
                setStatus('disconnected');
                ws = null;
            };
        });
    }

    function handleMessage(msg, connectResolve) {
        // Messages arrive as tagged CBOR: { _tag: N, _value: {...} }
        if (!msg || msg._tag === undefined) {
            console.warn('Untagged message:', msg);
            return;
        }

        const tag = msg._tag;
        const data = msg._value;

        switch (tag) {
            case Tag.AUTH:
                if (data.nonce) {
                    // AuthChallenge — resolve the connect promise so caller can authenticate
                    setStatus('challenge received');
                    if (connectResolve) connectResolve();
                } else if (data.success !== undefined) {
                    // AuthResponse
                    const pending = pendingRequests.get('auth');
                    if (pending) {
                        pendingRequests.delete('auth');
                        if (data.success) {
                            sessionId = data.sessionId;
                            setStatus('authenticated: ' + (sessionId || ''));
                            pending.resolve(data);
                        } else {
                            pending.reject(new Error(data.error || 'Auth failed'));
                        }
                    }
                }
                break;

            case Tag.CONTEXT: {
                const pending = pendingRequests.get('context');
                if (pending) {
                    pendingRequests.delete('context');
                    pending.resolve(data);
                }
                break;
            }

            case Tag.DISPATCH: {
                const reqId = data.requestId;
                const pending = pendingRequests.get(reqId);
                if (pending) {
                    pendingRequests.delete(reqId);
                    if (data.success) {
                        pending.resolve(data);
                    } else {
                        pending.reject(new Error(data.error || 'Dispatch failed'));
                    }
                }
                break;
            }

            case Tag.LOOKUP: {
                const reqId = data.requestId;
                const pending = pendingRequests.get(reqId);
                if (pending) {
                    pendingRequests.delete(reqId);
                    pending.resolve(data);
                }
                break;
            }

            case Tag.EVENT: {
                if (eventHandler) eventHandler(data);
                break;
            }

            case Tag.ACK:
            case Tag.HEARTBEAT:
                // Respond to heartbeat
                if (tag === Tag.HEARTBEAT) {
                    send(Tag.HEARTBEAT, {});
                }
                break;

            case Tag.ERROR:
                console.error('Protocol error:', data);
                break;
        }
    }

    function send(tag, obj) {
        if (!ws || ws.readyState !== WebSocket.OPEN) {
            throw new Error('Not connected');
        }
        const tagged = { _tag: tag, _value: obj };
        const bytes = CBOR.encode(tagged);
        ws.send(bytes.buffer);
    }

    function authenticate(token) {
        return new Promise((resolve, reject) => {
            pendingRequests.set('auth', { resolve, reject });
            send(Tag.AUTH, { token: token });
        });
    }

    function setContext(itemIdText) {
        return new Promise((resolve, reject) => {
            pendingRequests.set('context', { resolve, reject });
            send(Tag.CONTEXT, { itemId: itemIdText });
        });
    }

    function getContext() {
        return new Promise((resolve, reject) => {
            pendingRequests.set('context', { resolve, reject });
            send(Tag.CONTEXT, {});
        });
    }

    function dispatch(action, args) {
        const reqId = requestCounter++;
        return new Promise((resolve, reject) => {
            pendingRequests.set(reqId, { resolve, reject });
            send(Tag.DISPATCH, {
                action: action,
                args: args || [],
                requestId: reqId
            });
        });
    }

    function lookup(query, limit) {
        const reqId = requestCounter++;
        return new Promise((resolve, reject) => {
            pendingRequests.set(reqId, { resolve, reject });
            send(Tag.LOOKUP, {
                query: query,
                limit: limit || 10,
                requestId: reqId
            });
        });
    }

    function subscribe(itemIdText) {
        send(Tag.SUBSCRIBE, { itemId: itemIdText, subscribe: true });
    }

    function onEvent(handler) {
        eventHandler = handler;
    }

    function setStatus(text) {
        if (statusHandler) statusHandler(text);
        console.log('[session]', text);
    }

    function onStatus(handler) {
        statusHandler = handler;
    }

    function isConnected() {
        return ws && ws.readyState === WebSocket.OPEN;
    }

    return {
        connect,
        authenticate,
        setContext,
        getContext,
        dispatch,
        lookup,
        subscribe,
        onEvent,
        onStatus,
        isConnected
    };
})();
