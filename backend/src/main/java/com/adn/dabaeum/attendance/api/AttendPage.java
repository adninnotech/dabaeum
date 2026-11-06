package com.adn.dabaeum.attendance.api;

import java.util.UUID;

/**
 * 학생 폰이 QR 로 여는 페이지. 프론트 앱이 아니라 <b>중계기</b>다 — 다대구 앱으로 넘기고,
 * 결과가 나오면 보여주고 끝난다. 그래서 서버가 문자열로 만들어 내려준다.
 *
 * <p>결과는 다대구 공식 {@code didLogin.js} 와 같은 시점에 묻는다 — 앱이 앞에 있는 동안은 조용히,
 * 브라우저로 돌아온 순간(visibilitychange/focus/pageshow)에 서버 status 를 부른다. 서버는 그때
 * 다대구에 한 번 묻는다. 주기 폴링은 없다.
 *
 * <p>학생은 로그인하지 않으므로 이 화면 말고는 자기 출석이 됐는지 알 방법이 없다.
 * 결과를 안 보여주면 불안해서 다시 찍고, 그게 전부 중복 오류로 쌓인다.
 */
final class AttendPage {

    private AttendPage() {
    }

    static String render(UUID attendId, String deepLink) {
        return """
            <!doctype html><html lang="ko"><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>출석 인증</title>
            <style>
              body{font:16px/1.7 system-ui,-apple-system,sans-serif;text-align:center;
                   padding:40px 20px;color:#222}
              h1{font-size:20px;margin:0 0 24px}
              .msg{font-size:18px;margin:24px 0;min-height:2em}
              .ok{color:#1a7f37;font-weight:700;font-size:22px}
              .bad{color:#c62828;font-weight:700}
              .btn{display:inline-block;padding:14px 28px;background:#2f4fd8;color:#fff;
                   border:0;border-radius:10px;text-decoration:none;font-size:17px;cursor:pointer}
              .hint{color:#888;font-size:13px;margin-top:28px}
            </style>
            <h1>출석 인증</h1>
            <p class="msg" id="msg">다대구 앱을 엽니다…</p>
            <p><a class="btn" href="%s">앱이 안 열리면 여기를 누르세요</a></p>
            <p class="hint">인증을 마치면 이 화면으로 돌아옵니다.</p>
            <p><button class="btn" id="again">인증을 마쳤어요, 출석 확인</button></p>
            <script>
            var LINK = %s;
            var STATUS = %s;
            var REASON = {
              ATTENDANCE_CONFLICT: '이미 출석하셨습니다',
              ENROLLMENT_NOT_FOUND: '이 강좌 수강생이 아닙니다',
              ENROLLMENT_STATUS_CONFLICT: '수강신청이 승인되지 않았습니다',
              AUTHENTICATION_FAILED: '다배움에 연결된 계정이 없습니다'
            };
            var msg = document.getElementById('msg');
            var again = document.getElementById('again');
            var done = false, busy = false, timer = null, retries = 0;
            function show(html, cls) { msg.innerHTML = html; msg.className = 'msg ' + (cls || ''); }
            function finish(html, cls) { done = true; clearTimeout(timer); again.hidden = true; show(html, cls); }
            // 서버가 이 요청을 받을 때 다대구에 한 번 묻는다. 그래서 앱이 앞에 있는 동안은 부르지 않는다.
            function check() {
              if (done || busy) { return; }
              busy = true; clearTimeout(timer);
              show('출석을 확인하는 중…');
              fetch(STATUS, { cache: 'no-store' }).then(function (r) {
                if (!r.ok) { throw new Error('status ' + r.status); }
                return r.json();
              }).then(function (s) {
                var who = s.studentName ? s.studentName + '님, ' : '';
                if (s.state === 'RECORDED') { return finish(who + '출석 완료 &#10003;', 'ok'); }
                if (s.state === 'FAILED') {
                  return finish(who + (REASON[s.errorCode] || ('출석 처리에 실패했습니다 (' + s.errorCode + ')')), 'bad');
                }
                if (s.state === 'EXPIRED') { return finish('시간이 지났습니다. QR 을 다시 찍어 주세요', 'bad'); }
                show('다대구 앱에서 인증해 주세요');
                // 앱이 결과를 올리는 데 잠깐 걸릴 수 있다. 두 번만 더 묻고 학생에게 맡긴다.
                if (retries++ < 2) { timer = setTimeout(check, 3000); }
              }).catch(function () {
                show('확인하지 못했습니다. 아래 버튼을 다시 눌러 주세요', 'bad');
              }).then(function () { busy = false; });
            }
            function resume() { if (document.visibilityState !== 'hidden') { retries = 0; check(); } }
            document.addEventListener('visibilitychange', function () {
              if (document.visibilityState === 'hidden') { clearTimeout(timer); } else { resume(); }
            });
            window.addEventListener('focus', resume);
            window.addEventListener('pageshow', resume);
            again.onclick = function () { retries = 0; check(); };
            location.href = LINK;
            </script>
            </html>
            """.formatted(
                escapeAttribute(deepLink),
                jsonString(deepLink),
                jsonString("/attend/" + attendId + "/status")
            );
    }

    /** 토큰이 거부됐을 때. 학생이 뭘 해야 하는지만 알려준다. */
    static String error(String errorCode) {
        String reason = switch (errorCode) {
            case "ATTENDANCE_QR_EXPIRED" -> "QR 이 만료됐습니다. 화면의 새 QR 을 찍어 주세요";
            case "ATTENDANCE_QR_INVALID" -> "올바른 출석 QR 이 아닙니다";
            default -> "출석을 시작할 수 없습니다";
        };
        return """
            <!doctype html><html lang="ko"><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>출석 인증</title>
            <style>
              body{font:16px/1.7 system-ui,-apple-system,sans-serif;text-align:center;
                   padding:40px 20px;color:#222}
              .bad{color:#c62828;font-weight:700;font-size:18px}
              .code{color:#888;font-size:12px;margin-top:24px;font-family:monospace}
            </style>
            <p class="bad">%s</p>
            <p class="code">%s</p>
            </html>
            """.formatted(escapeText(reason), escapeText(errorCode));
    }

    private static String escapeText(String value) {
        return value == null ? "" : value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    private static String escapeAttribute(String value) {
        return escapeText(value).replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** 스크립트 안에 넣을 문자열 리터럴. {@code </script>} 로 태그를 닫지 못하게 막는다. */
    private static String jsonString(String value) {
        String escaped = (value == null ? "" : value)
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("<", "\\u003c")
            .replace(">", "\\u003e");
        return "\"" + escaped + "\"";
    }
}
