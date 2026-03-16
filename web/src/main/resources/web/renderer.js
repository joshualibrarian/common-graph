/**
 * DOM Scene Renderer for CG surfaces.
 *
 * Implements the SurfaceRenderer contract by building DOM elements.
 * The three primitives:
 *   - text(content, styles) → span/p
 *   - image(alt, resource, size, fit, styles) → img
 *   - beginBox(direction, styles, ...) / endBox() → div with flexbox
 *
 * Directions:
 *   - VERTICAL → flex-direction: column
 *   - HORIZONTAL → flex-direction: row
 *   - STACK → position: relative + absolute children
 *
 * Size units pass through to CSS (px, em, rem, ch, vw, vh, %).
 * The CG unit 'ln' is mapped to CSS 'lh'.
 */
const Renderer = (() => {

    // Stack of container elements (current parent at top)
    let containerStack = [];
    // Pending metadata for the next element
    let pendingId = null;
    let pendingType = null;
    let pendingEditable = false;
    let pendingEvents = [];

    function clear(rootEl) {
        rootEl.innerHTML = '';
        containerStack = [rootEl];
        pendingId = null;
        pendingType = null;
        pendingEditable = false;
        pendingEvents = [];
    }

    function currentContainer() {
        return containerStack[containerStack.length - 1];
    }

    function mapUnit(value) {
        // Map CG's 'ln' unit to CSS 'lh'
        if (typeof value === 'string') {
            return value.replace(/(\d+(?:\.\d+)?)\s*ln/g, '$1lh');
        }
        return value;
    }

    function applyCommon(el) {
        if (pendingId) { el.id = pendingId; pendingId = null; }
        if (pendingType) { el.dataset.type = pendingType; pendingType = null; }
        if (pendingEditable) { el.contentEditable = 'true'; pendingEditable = false; }
        for (const evt of pendingEvents) {
            el.addEventListener(evt.on, () => {
                if (Session.isConnected()) {
                    Session.dispatch(evt.action, evt.target ? [evt.target] : []);
                }
            });
        }
        pendingEvents = [];
    }

    // --- Primitives ---

    function text(content, styles) {
        const el = document.createElement('span');
        el.textContent = content || '';
        if (styles) el.className = styles.join(' ');
        applyCommon(el);
        currentContainer().appendChild(el);
    }

    function formattedText(content, format, styles) {
        if (format === 'code') {
            const pre = document.createElement('pre');
            const code = document.createElement('code');
            code.textContent = content || '';
            pre.appendChild(code);
            if (styles) pre.className = styles.join(' ');
            applyCommon(pre);
            currentContainer().appendChild(pre);
        } else {
            text(content, styles);
        }
    }

    function richText(spans, paragraphStyles) {
        const p = document.createElement('p');
        if (paragraphStyles) p.className = paragraphStyles.join(' ');
        for (const span of (spans || [])) {
            const s = document.createElement('span');
            s.textContent = span.text || '';
            if (span.styles) s.className = span.styles.join(' ');
            p.appendChild(s);
        }
        applyCommon(p);
        currentContainer().appendChild(p);
    }

    function image(alt, resource, size, fit, styles) {
        if (resource) {
            const img = document.createElement('img');
            img.alt = alt || '';
            img.src = resource;
            if (size) img.style.width = mapUnit(size);
            if (fit) img.style.objectFit = fit;
            if (styles) img.className = styles.join(' ');
            applyCommon(img);
            currentContainer().appendChild(img);
        } else if (alt) {
            // No image resource — show alt text
            const el = document.createElement('span');
            el.textContent = alt;
            el.className = (styles || []).concat('image-alt').join(' ');
            applyCommon(el);
            currentContainer().appendChild(el);
        }
    }

    function beginBox(direction, styles, border, background, width, height, padding) {
        const el = document.createElement('div');
        el.className = (styles || []).join(' ');

        // Direction → flexbox
        switch (direction) {
            case 'STACK':
                el.style.position = 'relative';
                el.classList.add('cg-stack');
                break;
            case 'HORIZONTAL':
                el.style.display = 'flex';
                el.style.flexDirection = 'row';
                break;
            case 'VERTICAL':
            default:
                el.style.display = 'flex';
                el.style.flexDirection = 'column';
                break;
        }

        // Box properties
        if (background) el.style.background = background;
        if (width) el.style.width = mapUnit(width);
        if (height) el.style.height = mapUnit(height);
        if (padding) el.style.padding = mapUnit(padding);

        // Border
        if (border) applyBorder(el, border);

        applyCommon(el);
        currentContainer().appendChild(el);
        containerStack.push(el);
    }

    function endBox() {
        if (containerStack.length > 1) {
            containerStack.pop();
        }
    }

    function applyBorder(el, border) {
        if (typeof border === 'string') {
            el.style.border = border;
        } else if (typeof border === 'object') {
            if (border.top) el.style.borderTop = border.top;
            if (border.right) el.style.borderRight = border.right;
            if (border.bottom) el.style.borderBottom = border.bottom;
            if (border.left) el.style.borderLeft = border.left;
            if (border.radius) el.style.borderRadius = mapUnit(border.radius);
        }
    }

    // --- Layout hints ---

    function gap(spec) {
        const el = currentContainer();
        if (el) el.style.gap = mapUnit(spec);
    }

    function maxWidth(spec) {
        const el = currentContainer();
        if (el) el.style.maxWidth = mapUnit(spec);
    }

    function maxHeight(spec) {
        const el = currentContainer();
        if (el) el.style.maxHeight = mapUnit(spec);
    }

    function overflow(spec) {
        const el = currentContainer();
        if (el) el.style.overflow = spec;
    }

    function aspectRatio(spec) {
        const el = currentContainer();
        if (el) el.style.aspectRatio = spec;
    }

    // --- Metadata ---

    function setId(id) { pendingId = id; }
    function setType(type) { pendingType = type; }
    function setEditable(editable) { pendingEditable = editable; }
    function addEvent(on, action, target) {
        pendingEvents.push({ on, action, target });
    }

    // --- Text properties ---

    function textFontSize(spec) {
        const el = currentContainer();
        if (el) el.style.fontSize = mapUnit(spec);
    }

    function textFontFamily(family) {
        const el = currentContainer();
        if (el) el.style.fontFamily = family;
    }

    // --- Shape (SVG) ---

    function shape(type, cornerRadius, fill, stroke, strokeWidth, path) {
        const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        svg.style.overflow = 'visible';

        let el;
        switch (type) {
            case 'circle':
                el = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
                el.setAttribute('cx', '50%');
                el.setAttribute('cy', '50%');
                el.setAttribute('r', '50%');
                break;
            case 'path':
                el = document.createElementNS('http://www.w3.org/2000/svg', 'path');
                if (path) el.setAttribute('d', path);
                break;
            default: // rectangle
                el = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
                el.setAttribute('width', '100%');
                el.setAttribute('height', '100%');
                if (cornerRadius) el.setAttribute('rx', cornerRadius);
                break;
        }

        if (fill) el.setAttribute('fill', fill);
        if (stroke) el.setAttribute('stroke', stroke);
        if (strokeWidth) el.setAttribute('stroke-width', strokeWidth);

        svg.appendChild(el);
        applyCommon(svg);
        currentContainer().appendChild(svg);
    }

    // --- Rotation/Transform ---

    function rotation(degrees) {
        const el = currentContainer();
        if (el) el.style.transform = `rotate(${degrees}deg)`;
    }

    function transformOrigin(xFrac, yFrac) {
        const el = currentContainer();
        if (el) el.style.transformOrigin = `${xFrac * 100}% ${yFrac * 100}%`;
    }

    // --- Render instruction replay ---

    /**
     * Replay a render instruction stream (array of [method, ...args] arrays)
     * produced by RenderInstructionRecorder on the server.
     */
    function replay(instructions, rootEl) {
        clear(rootEl);
        for (const instr of instructions) {
            const [method, ...args] = instr;
            switch (method) {
                case 'text':          text(args[0], args[1]); break;
                case 'formattedText': formattedText(args[0], args[1], args[2]); break;
                case 'richText':      richText(args[0], args[1]); break;
                case 'image':         image(args[0], args[1], args[2], args[3], args[4]); break;
                case 'beginBox':      beginBox(args[0], args[1], args[2], args[3], args[4], args[5], args[6]); break;
                case 'endBox':        endBox(); break;
                case 'gap':           gap(args[0]); break;
                case 'maxWidth':      maxWidth(args[0]); break;
                case 'maxHeight':     maxHeight(args[0]); break;
                case 'overflow':      overflow(args[0]); break;
                case 'aspectRatio':   aspectRatio(args[0]); break;
                case 'id':            setId(args[0]); break;
                case 'type':          setType(args[0]); break;
                case 'editable':      setEditable(args[0]); break;
                case 'event':         addEvent(args[0], args[1], args[2]); break;
                case 'fontSize':      textFontSize(args[0]); break;
                case 'fontFamily':    textFontFamily(args[0]); break;
                case 'shape':         shape(args[0], args[1], args[2], args[3], args[4], args[5]); break;
                case 'rotation':      rotation(args[0]); break;
                default:
                    // Unknown instruction — skip (forward compatibility)
                    console.debug('Unknown render instruction:', method);
            }
        }
    }

    return {
        clear, replay,
        text, formattedText, richText, image,
        beginBox, endBox,
        gap, maxWidth, maxHeight, overflow, aspectRatio,
        setId, setType, setEditable, addEvent,
        textFontSize, textFontFamily,
        shape, rotation, transformOrigin
    };
})();
