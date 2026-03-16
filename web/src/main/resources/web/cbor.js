/**
 * Minimal CBOR encoder/decoder for the CG session protocol.
 *
 * Handles: unsigned/negative ints, byte/text strings, arrays, maps,
 * booleans, null, and CBOR tags (used for protocol message discrimination).
 *
 * Does NOT handle: IEEE 754 floats (CG doesn't use them), indefinite-length
 * encoding (CG uses deterministic CBOR).
 */
const CBOR = (() => {
    // --- Decoder ---

    class Decoder {
        constructor(buffer) {
            this.data = new DataView(buffer);
            this.offset = 0;
        }

        decode() {
            const initial = this.data.getUint8(this.offset++);
            const major = initial >> 5;
            const info = initial & 0x1f;

            switch (major) {
                case 0: return this.readUint(info);                      // unsigned int
                case 1: return -1 - this.readUint(info);                 // negative int
                case 2: return this.readBytes(this.readUint(info));      // byte string
                case 3: return this.readText(this.readUint(info));       // text string
                case 4: return this.readArray(this.readUint(info));      // array
                case 5: return this.readMap(this.readUint(info));        // map
                case 6: return this.readTag(info);                       // tag
                case 7: return this.readSimple(info);                    // simple/float
                default: throw new Error(`Unknown CBOR major type: ${major}`);
            }
        }

        readUint(info) {
            if (info < 24) return info;
            if (info === 24) return this.data.getUint8(this.offset++);
            if (info === 25) { const v = this.data.getUint16(this.offset); this.offset += 2; return v; }
            if (info === 26) { const v = this.data.getUint32(this.offset); this.offset += 4; return v; }
            if (info === 27) {
                // 64-bit: read as two 32-bit values, return as Number (loses precision >2^53)
                const hi = this.data.getUint32(this.offset);
                const lo = this.data.getUint32(this.offset + 4);
                this.offset += 8;
                return hi * 0x100000000 + lo;
            }
            throw new Error(`Invalid CBOR additional info: ${info}`);
        }

        readBytes(len) {
            const bytes = new Uint8Array(this.data.buffer, this.data.byteOffset + this.offset, len);
            this.offset += len;
            return new Uint8Array(bytes); // copy
        }

        readText(len) {
            const bytes = new Uint8Array(this.data.buffer, this.data.byteOffset + this.offset, len);
            this.offset += len;
            return new TextDecoder().decode(bytes);
        }

        readArray(len) {
            const arr = [];
            for (let i = 0; i < len; i++) arr.push(this.decode());
            return arr;
        }

        readMap(len) {
            const map = {};
            for (let i = 0; i < len; i++) {
                const key = this.decode();
                map[key] = this.decode();
            }
            return map;
        }

        readTag(info) {
            const tagNumber = this.readUint(info);
            const value = this.decode();
            return { _tag: tagNumber, _value: value };
        }

        readSimple(info) {
            const val = info < 24 ? info : this.data.getUint8(this.offset++);
            if (val === 20) return false;
            if (val === 21) return true;
            if (val === 22) return null;
            if (val === 23) return undefined;
            // Float16/32/64 — CG shouldn't send these, but handle gracefully
            if (info === 25) { this.offset += 2; return 0; }
            if (info === 26) { const v = this.data.getFloat32(this.offset); this.offset += 4; return v; }
            if (info === 27) { const v = this.data.getFloat64(this.offset); this.offset += 8; return v; }
            return val;
        }
    }

    // --- Encoder ---

    class Encoder {
        constructor() {
            this.chunks = [];
        }

        encode(value) {
            if (value === null || value === undefined) {
                this.writeByte(0xf6); // null
            } else if (typeof value === 'boolean') {
                this.writeByte(value ? 0xf5 : 0xf4);
            } else if (typeof value === 'number') {
                if (Number.isInteger(value) && value >= 0) {
                    this.writeUint(0, value);
                } else if (Number.isInteger(value) && value < 0) {
                    this.writeUint(1, -1 - value);
                } else {
                    // Float64 fallback (shouldn't be needed for CG)
                    this.writeByte(0xfb);
                    const buf = new ArrayBuffer(8);
                    new DataView(buf).setFloat64(0, value);
                    this.chunks.push(new Uint8Array(buf));
                }
            } else if (typeof value === 'string') {
                const bytes = new TextEncoder().encode(value);
                this.writeUint(3, bytes.length);
                this.chunks.push(bytes);
            } else if (value instanceof Uint8Array) {
                this.writeUint(2, value.length);
                this.chunks.push(value);
            } else if (Array.isArray(value)) {
                this.writeUint(4, value.length);
                for (const item of value) this.encode(item);
            } else if (value._tag !== undefined) {
                // Tagged value
                this.writeUint(6, value._tag);
                this.encode(value._value);
            } else if (typeof value === 'object') {
                const keys = Object.keys(value);
                this.writeUint(5, keys.length);
                for (const key of keys) {
                    this.encode(key);
                    this.encode(value[key]);
                }
            }
        }

        writeUint(major, value) {
            const m = major << 5;
            if (value < 24) {
                this.writeByte(m | value);
            } else if (value < 256) {
                this.writeByte(m | 24);
                this.writeByte(value);
            } else if (value < 65536) {
                this.writeByte(m | 25);
                const buf = new ArrayBuffer(2);
                new DataView(buf).setUint16(0, value);
                this.chunks.push(new Uint8Array(buf));
            } else if (value < 0x100000000) {
                this.writeByte(m | 26);
                const buf = new ArrayBuffer(4);
                new DataView(buf).setUint32(0, value);
                this.chunks.push(new Uint8Array(buf));
            } else {
                this.writeByte(m | 27);
                const buf = new ArrayBuffer(8);
                const dv = new DataView(buf);
                dv.setUint32(0, (value / 0x100000000) >>> 0);
                dv.setUint32(4, value >>> 0);
                this.chunks.push(new Uint8Array(buf));
            }
        }

        writeByte(b) {
            this.chunks.push(new Uint8Array([b]));
        }

        toBytes() {
            let totalLen = 0;
            for (const c of this.chunks) totalLen += c.length;
            const result = new Uint8Array(totalLen);
            let offset = 0;
            for (const c of this.chunks) {
                result.set(c, offset);
                offset += c.length;
            }
            return result;
        }
    }

    return {
        decode(buffer) {
            const decoder = new Decoder(buffer);
            return decoder.decode();
        },

        encode(value) {
            const encoder = new Encoder();
            encoder.encode(value);
            return encoder.toBytes();
        }
    };
})();
