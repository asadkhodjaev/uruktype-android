"""Add Uzbek long-press letters to existing compiled keyboard XML only.

Does not modify executable code, native libraries or application permissions.
Run against an unsigned merged APK; sign the output separately.
"""
import argparse
import json
from pathlib import Path
import struct
import zipfile


def chunks(data, start=8):
    pos = start
    while pos < len(data):
        kind, header, size = struct.unpack_from('<HHI', data, pos)
        assert size >= header >= 8 and pos + size <= len(data)
        yield kind, header, bytearray(data[pos:pos + size])
        pos += size
    assert pos == len(data)


class Xml:
    def __init__(self, data):
        self.chunks = list(chunks(data))
        pool = next(c for t, h, c in self.chunks if t == 1)
        _, header, _ = struct.unpack_from('<HHI', pool)
        count, styles, self.flags, start, _ = struct.unpack_from('<5I', pool, 8)
        assert styles == 0
        self.strings = []
        for i in range(count):
            pos = start + struct.unpack_from('<I', pool, header + i * 4)[0]
            if self.flags & 256:
                def length(p):
                    a = pool[p]
                    return (((a & 127) << 8) | pool[p + 1], p + 2) if a & 128 else (a, p + 1)
                _, pos = length(pos)
                n, pos = length(pos)
                value = bytes(pool[pos:pos + n]).decode('utf-8')
            else:
                n = struct.unpack_from('<H', pool, pos)[0]
                pos += 2
                if n & 32768:
                    n = ((n & 32767) << 16) | struct.unpack_from('<H', pool, pos)[0]
                    pos += 2
                value = bytes(pool[pos:pos + n * 2]).decode('utf-16le')
            self.strings.append(value)
        maps = [c for t, h, c in self.chunks if t == 0x180]
        self.resource_ids = list(struct.unpack('<' + 'I' * ((len(maps[0]) - 8) // 4), maps[0][8:])) if maps else []

    def text(self, index):
        return self.strings[index] if index != 0xffffffff else ''

    def string(self, value):
        if value not in self.strings:
            self.strings.append(value)
        return self.strings.index(value)

    def key_attributes(self, node):
        start, size, count = struct.unpack_from('<HHH', node, 24)
        assert size == 20
        return [bytearray(node[16 + start + i * size:16 + start + (i + 1) * size]) for i in range(count)]

    def attributes(self, node):
        return {self.text(struct.unpack_from('<I', a, 4)[0]): a for a in self.key_attributes(node)}

    def value(self, attribute):
        return self.text(struct.unpack_from('<I', attribute, 16)[0]) if attribute[15] == 3 else None

    def set_string(self, attrs, name, value, global_attrs):
        if name in attrs:
            attr = attrs[name]
        else:
            namespace, resource_id = global_attrs[name]
            index = self.string(name)
            while len(self.resource_ids) <= index:
                self.resource_ids.append(0)
            self.resource_ids[index] = resource_id
            attr = bytearray(struct.pack('<IIIHBBI', self.string(namespace), index, 0, 8, 0, 3, 0))
            attrs[name] = attr
        index = self.string(value)
        struct.pack_into('<I', attr, 8, index)
        struct.pack_into('<HBBI', attr, 12, 8, 0, 3, index)

    def pool_bytes(self):
        offsets, values = [], bytearray()
        def length8(n):
            assert n < 32768
            return bytes([(n >> 8) | 128, n & 255]) if n > 127 else bytes([n])
        for value in self.strings:
            offsets.append(len(values))
            if self.flags & 256:
                b = value.encode('utf-8')
                values += length8(len(value.encode('utf-16le')) // 2) + length8(len(b)) + b + b'\0'
            else:
                b = value.encode('utf-16le')
                n = len(b) // 2
                assert n < 32768
                values += struct.pack('<H', n) + b + b'\0\0'
        values += bytes((-len(values)) % 4)
        start = 28 + 4 * len(offsets)
        return struct.pack('<HHI5I', 1, 28, start + len(values), len(offsets), 0, self.flags & ~1, start, 0) + struct.pack('<' + 'I' * len(offsets), *offsets) + values

    def build(self):
        result = bytearray()
        for kind, header, chunk in self.chunks:
            if kind == 1:
                result += self.pool_bytes()
            elif kind == 0x180:
                result += struct.pack('<HHI', 0x180, 8, 8 + len(self.resource_ids) * 4) + struct.pack('<' + 'I' * len(self.resource_ids), *self.resource_ids)
            else:
                result += chunk
        return struct.pack('<HHI', 3, 8, len(result) + 8) + result


def patch(data, letters, global_attrs):
    doc, changed = Xml(data), []
    for i, (kind, header, node) in enumerate(doc.chunks):
        if kind != 0x102 or doc.text(struct.unpack_from('<I', node, 20)[0]) != 'Key':
            continue
        attrs = doc.attributes(node)
        if 'keySpec' not in attrs:
            continue
        primary = doc.value(attrs['keySpec'])
        if primary not in letters:
            continue
        alternate = letters[primary]
        old_more = doc.value(attrs['moreKeys']) if 'moreKeys' in attrs else ''
        more = alternate + (',' + old_more if old_more and alternate not in old_more.split(',') else '')
        doc.set_string(attrs, 'moreKeys', more, global_attrs)
        doc.set_string(attrs, 'keyHintLabel', alternate, global_attrs)
        ordered = sorted(attrs.values(), key=lambda a: doc.resource_ids[struct.unpack_from('<I', a, 4)[0]])
        start = struct.unpack_from('<H', node, 24)[0]
        node = node[:16 + start] + b''.join(ordered)
        struct.pack_into('<I', node, 4, len(node))
        struct.pack_into('<H', node, 28, len(ordered))
        doc.chunks[i] = kind, header, node
        changed.append((primary, alternate))
    result = doc.build() if changed else data
    if changed:
        # Decode the produced XML independently and confirm the actual key values.
        check = Xml(result)
        for kind, _, node in check.chunks:
            if kind == 0x102 and check.text(struct.unpack_from('<I', node, 20)[0]) == 'Key':
                attrs = check.attributes(node)
                primary = check.value(attrs['keySpec']) if 'keySpec' in attrs else None
                if primary in letters:
                    assert check.value(attrs['keyHintLabel']) == letters[primary]
                    assert check.value(attrs['moreKeys']).split(',')[0] == letters[primary]
    return result, changed


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('input')
    parser.add_argument('output')
    args = parser.parse_args()
    zin = zipfile.ZipFile(args.input)
    global_attrs = {}
    for name in ['res/xml/keyboard_rowkeys_qwerty1.xml', 'res/xml/keyboard_rowkeys_east_slavic1.xml']:
        doc = Xml(zin.read(name))
        for kind, _, node in doc.chunks:
            if kind == 0x102:
                for attr in doc.key_attributes(node):
                    index = struct.unpack_from('<I', attr, 4)[0]
                    if index < len(doc.resource_ids) and doc.resource_ids[index]:
                        global_attrs[doc.text(index)] = (doc.text(struct.unpack_from('<I', attr)[0]), doc.resource_ids[index])
    report = {}
    latin = dict(o='ö', g='ğ', s='ş', c='ç')
    cyrillic = {'у':'ў','г':'ғ','к':'қ','х':'ҳ'}
    with zipfile.ZipFile(args.output, 'w') as zout:
        for info in zin.infolist():
            if info.filename.upper().startswith('META-INF/') and info.filename.upper().endswith(('.RSA','.DSA','.EC','.SF','MANIFEST.MF')):
                continue
            data = zin.read(info.filename)
            letters = None
            if info.filename.startswith('res/xml/'):
                if 'keyboard_rowkeys_qwerty' in info.filename or 'qwerty_tablet_row' in info.filename:
                    letters = latin
                elif 'keyboard_rowkeys_east_slavic1' in info.filename or 'east_slavic_tablet_row_1_letters' in info.filename:
                    letters = cyrillic
            if letters:
                data, changes = patch(data, letters, global_attrs)
                if changes:
                    report[info.filename] = changes
            zout.writestr(info, data)
    Path(args.output + '.changes.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
    assert set(alternate for changes in report.values() for _, alternate in changes) == set('öğşçўғқҳ')
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
