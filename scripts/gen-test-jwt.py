#!/usr/bin/env python3
"""
로컬 테스트용 JWT 발급 스크립트. JWT_SECRET을 네트워크로 보내지 않고
이 컴퓨터 안에서만 서명한다. 절대 커밋/공유하지 말 것 — .gitignore에 이미 제외됨.

사용법:
  JWT_SECRET="실제시크릿값" python3 scripts/gen-test-jwt.py 1
  (1은 테스트할 userId)
"""
import base64
import hashlib
import hmac
import json
import os
import sys
import time


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def main():
    secret = os.environ.get("JWT_SECRET")
    if not secret:
        print("JWT_SECRET 환경변수를 먼저 설정하세요 (예: JWT_SECRET=xxx python3 ...)", file=sys.stderr)
        sys.exit(1)
    if len(sys.argv) < 2:
        print("사용법: python3 gen-test-jwt.py <userId> [유효시간(초), 기본 1800]", file=sys.stderr)
        sys.exit(1)

    user_id = sys.argv[1]
    ttl = int(sys.argv[2]) if len(sys.argv) > 2 else 1800

    header = {"alg": "HS256", "typ": "JWT"}
    now = int(time.time())
    payload = {"sub": str(user_id), "iat": now, "exp": now + ttl}

    signing_input = f"{b64url(json.dumps(header, separators=(',', ':')).encode())}.{b64url(json.dumps(payload, separators=(',', ':')).encode())}"
    signature = hmac.new(secret.encode(), signing_input.encode(), hashlib.sha256).digest()
    token = f"{signing_input}.{b64url(signature)}"
    print(token)


if __name__ == "__main__":
    main()
