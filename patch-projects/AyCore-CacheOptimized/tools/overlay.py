#!/usr/bin/env python3
from pathlib import Path
import sys, zipfile
if len(sys.argv) != 4:
    raise SystemExit('usage: overlay.py <original.jar> <compiled-classes-dir> <output.jar>')
original=Path(sys.argv[1]); compiled=Path(sys.argv[2]); output=Path(sys.argv[3])
replaced={
 'com/aystudio/core/bukkit/util/common/ReflectionUtil.class',
 'com/aystudio/core/bukkit/nms/INMSClass.class',
}
added=[
 'com/aystudio/core/bukkit/util/common/ReflectionUtil$1.class',
 'com/aystudio/core/bukkit/util/common/ReflectionUtil$MethodCache.class',
 'com/aystudio/core/bukkit/util/common/ReflectionUtil$MethodKey.class',
 'com/aystudio/core/bukkit/nms/INMSClass$1.class',
 'com/aystudio/core/bukkit/nms/INMSClass$FieldKey.class',
]
with zipfile.ZipFile(original,'r') as zin, zipfile.ZipFile(output,'w') as zout:
    for info in zin.infolist():
        data=(compiled/info.filename).read_bytes() if info.filename in replaced else zin.read(info.filename)
        ni=zipfile.ZipInfo(info.filename, date_time=info.date_time)
        ni.compress_type=info.compress_type; ni.comment=info.comment; ni.extra=info.extra
        ni.internal_attr=info.internal_attr; ni.external_attr=info.external_attr
        ni.create_system=info.create_system; ni.flag_bits=info.flag_bits
        zout.writestr(ni,data)
    for name in added:
        p=compiled/name
        info=zipfile.ZipInfo(name)
        info.compress_type=zipfile.ZIP_DEFLATED
        info.external_attr=(0o100644 << 16); info.create_system=3
        zout.writestr(info,p.read_bytes())
