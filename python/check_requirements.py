import pkg_resources
from pkg_resources import DistributionNotFound, VersionConflict
import sys
import platform

print("Short version of python is:\t{0}".format(platform.python_version()))
print("Full version of python is:\t{0}".format(sys.version))

dependencies = []
with open('requirements.txt', 'rt') as f:
    for line in f:
        requirement = line.rstrip()
        dependencies.append(requirement)

ok = []
dnf = []
vc = []
for req in dependencies:
    try:
        pkg_resources.require(req)
        ok.append(req)
    except DistributionNotFound as e:
        dnf.append(req)
    except VersionConflict as e:
        vc.append(req)



print("\nPackages are ok:")
for req in ok:
    print("\t{0}".format(req))

print("\nDistributionNotFound error:")
for req in dnf:
    print("\t{0}".format(req))

print("\nVersionConflict error:")
for req in vc:
    print("\t{0}".format(req))
