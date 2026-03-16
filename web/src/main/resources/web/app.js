/**
 * CG Web Client — application bootstrap.
 *
 * Connects to a Librarian's web gateway via WebSocket, authenticates,
 * and renders CG scenes in the browser.
 */
(function () {
    const root = document.getElementById('root');
    const input = document.getElementById('command');
    const status = document.getElementById('status');

    // Status updates
    Session.onStatus((text) => {
        status.textContent = text;
    });

    // Event handling (subscription pushes)
    Session.onEvent(async (event) => {
        // Re-render on item updates
        if (event.eventType === 'updated') {
            await refreshView();
        }
    });

    // Command input
    input.addEventListener('keydown', async (e) => {
        if (e.key === 'Enter') {
            const cmd = input.value.trim();
            if (!cmd) return;
            input.value = '';

            try {
                const result = await Session.dispatch(cmd, []);
                renderDispatchResult(result);
            } catch (err) {
                renderError(err.message);
            }
        }
    });

    async function refreshView() {
        try {
            // Dispatch empty "show" to get current view
            const result = await Session.dispatch('show', []);
            renderDispatchResult(result);
        } catch (err) {
            // Silently ignore refresh errors
            console.debug('Refresh failed:', err);
        }
    }

    function renderDispatchResult(result) {
        if (result.view) {
            const view = result.view;

            // If the view has render instructions, replay them
            if (view.instructions) {
                Renderer.replay(view.instructions, root);
                return;
            }

            // If the view has a title, show it
            if (view.title) {
                root.innerHTML = '';
                const h = document.createElement('h2');
                h.textContent = view.title;
                root.appendChild(h);
            }

            // Fallback: show view as text
            if (view.type === 'View' && !view.instructions) {
                // The stub viewToCbor only sends {"type": "View"}
                // Until RenderInstructionRecorder is built, show a message
                const msg = document.createElement('div');
                msg.className = 'muted';
                msg.textContent = '(view rendering pending — server sent stub view)';
                root.appendChild(msg);
            }
        }
    }

    function renderError(message) {
        const el = document.createElement('div');
        el.className = 'error';
        el.textContent = message;
        root.appendChild(el);

        // Auto-remove after 5 seconds
        setTimeout(() => el.remove(), 5000);
    }

    // --- Startup ---

    async function start() {
        // Determine WebSocket URL (same host, /ws path)
        const wsProtocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${wsProtocol}//${location.host}/ws`;

        // Get token from URL params or prompt
        const params = new URLSearchParams(location.search);
        let token = params.get('token');

        try {
            await Session.connect(wsUrl);

            if (token) {
                await Session.authenticate(token);
                // Clear token from URL for security
                history.replaceState(null, '', location.pathname);
            } else {
                // Show auth prompt
                root.innerHTML = `
                    <div class="auth-prompt">
                        <h2>Common Graph</h2>
                        <p>Enter your session token to connect:</p>
                        <input id="token-input" type="password" placeholder="token..." autofocus>
                        <button id="token-submit">Connect</button>
                    </div>
                `;

                const tokenInput = document.getElementById('token-input');
                const tokenSubmit = document.getElementById('token-submit');

                const doAuth = async () => {
                    const t = tokenInput.value.trim();
                    if (!t) return;
                    try {
                        await Session.authenticate(t);
                        root.innerHTML = '';
                        input.focus();
                        await refreshView();
                    } catch (err) {
                        renderError('Authentication failed: ' + err.message);
                    }
                };

                tokenInput.addEventListener('keydown', (e) => {
                    if (e.key === 'Enter') doAuth();
                });
                tokenSubmit.addEventListener('click', doAuth);
                return;
            }

            input.focus();
            await refreshView();
        } catch (err) {
            renderError('Connection failed: ' + err.message);
        }
    }

    start();
})();
