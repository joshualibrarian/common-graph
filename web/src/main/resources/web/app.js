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
        if (event.eventType === 'updated') {
            await refreshView();
        }
    });

    // Command input — split first word as action, rest as args
    input.addEventListener('keydown', async (e) => {
        if (e.key === 'Enter') {
            const cmd = input.value.trim();
            if (!cmd) return;
            input.value = '';

            const parts = cmd.split(/\s+/);
            const action = parts[0];
            const args = parts.slice(1);

            try {
                const result = await Session.dispatch(action, args);
                renderDispatchResult(result);
            } catch (err) {
                renderError(err.message);
            }
        }
    });

    async function refreshView() {
        // Dispatch "list" to get the Librarian's compiled scene view.
        // After the dispatch refactor, every dispatch response includes
        // the context item's compiled @Scene view.
        try {
            const result = await Session.dispatch('list', []);
            renderDispatchResult(result);
        } catch (err) {
            // Fall back to context info if dispatch fails
            try {
                const ctx = await Session.getContext();
                showContextInfo(ctx);
            } catch (ctxErr) {
                console.debug('Context fetch failed:', ctxErr);
            }
        }
    }

    function showContextInfo(ctx) {
        root.innerHTML = '';

        const header = document.createElement('div');
        header.style.display = 'flex';
        header.style.flexDirection = 'column';
        header.style.gap = '1em';
        header.style.padding = '1em';

        // Title
        const title = document.createElement('h2');
        title.className = 'heading';
        title.textContent = ctx.label || 'Common Graph';
        header.appendChild(title);

        // Context info
        if (ctx.itemId) {
            const info = document.createElement('div');
            info.className = 'muted';
            info.textContent = 'context: ' + ctx.itemId;
            header.appendChild(info);
        }

        // Help text
        const help = document.createElement('div');
        help.className = 'muted';
        help.style.marginTop = '1em';
        help.innerHTML = 'Try commands: <span class="primary">list</span>, ' +
            '<span class="primary">create chess</span>, ' +
            '<span class="primary">help</span>';
        header.appendChild(help);

        root.appendChild(header);

        // Output area for command results
        const output = document.createElement('div');
        output.id = 'output';
        output.style.padding = '0 1em';
        root.appendChild(output);
    }

    function renderDispatchResult(result) {
        // Get or create output area
        let output = document.getElementById('output');
        if (!output) {
            output = document.createElement('div');
            output.id = 'output';
            output.style.padding = '0 1em';
            root.appendChild(output);
        }

        if (result.view) {
            const view = result.view;

            // If the view has render instructions, replay them
            if (view.instructions && view.instructions.length > 0) {
                // Create a fresh container for this result
                const container = document.createElement('div');
                container.style.marginTop = '0.5em';
                container.style.borderTop = '1px solid var(--surface0)';
                container.style.paddingTop = '0.5em';
                output.innerHTML = '';
                output.appendChild(container);
                Renderer.replay(view.instructions, container);
                return;
            }
        }

        // No view — show as text if there's an error
        if (result.error) {
            renderError(result.error);
        }
    }

    function renderError(message) {
        let output = document.getElementById('output');
        if (!output) {
            output = root;
        }

        const el = document.createElement('div');
        el.className = 'error';
        el.textContent = message;
        output.appendChild(el);

        // Auto-remove after 8 seconds
        setTimeout(() => el.remove(), 8000);
    }

    // --- Startup ---

    async function start() {
        const wsProtocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${wsProtocol}//${location.host}/ws`;

        const params = new URLSearchParams(location.search);
        let token = params.get('token');

        try {
            await Session.connect(wsUrl);

            if (token) {
                await Session.authenticate(token);
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
