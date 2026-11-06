"""pytest 공통 설정. 저장소 루트에서 `python3 -m pytest ml/recommend/tests` 로 실행해도 패키지를 찾게 한다."""

import sys
from pathlib import Path

PACKAGE_ROOT = Path(__file__).resolve().parents[1]
if str(PACKAGE_ROOT) not in sys.path:
    sys.path.insert(0, str(PACKAGE_ROOT))
