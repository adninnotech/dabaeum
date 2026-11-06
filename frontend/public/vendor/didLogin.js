/**
 * DID Login Library v3.0.0
 * 대구광역시 DID 로그인 서비스 연동 라이브러리
 *
 * 사용법:
 * 1. HTML에 스크립트 포함: <script src="didLogin.js"></script>
 * 2. 초기화: DIDLogin.init() 또는 DIDLogin.init(options)
 * 3. 로그인 실행: DIDLogin.login(loginData)
 *
 * 지원 브라우저 (별도 폴리필/트랜스파일 미사용 기준):
 * - Chrome 66+
 * - Firefox 57+
 * - Safari 12.1+
 * - Edge 17+
 * - Internet Explorer: 미지원
 *
 * @version 3.0.0
 * @author Daegu Metropolitan City
 */

(function(global) {
    'use strict';

    if (global.DIDLogin) {
        console.warn('DIDLogin library is already loaded');
        return;
    }

    const CONSTANTS = {
        VERSION: '3.0.0',
        BASE_URL: 'https://diddapp.daegu.go.kr/dgbp',
        TIMEOUT_DURATION: 60000,
        APP_LAUNCH_DELAY: 2500,
        APP_LAUNCH_TIMEOUT: 3000,
        IOS_APP_ID: '1565818679',
        ANDROID_PACKAGE: 'com.dreamsecurity.daegudid',
        POPUP_TARGET: 'dqr_login_popup_' + Date.now(),
        FLAGS: {
            PERSONAL: 'dqrp',
            CORPORATE: 'corp'
        },
        ADAPTIVE_POLLING: {
            MIN_INTERVAL: 1000,
            MAX_INTERVAL: 5000,
            MAX_FAILURES: 3,
            DEBOUNCE_DELAY: 300
        },
        TIMING: {
            IOS_AUTO_CHECK_DELAY: 100,
            POPUP_CHECK_INTERVAL: 1000,
            VISIBILITY_DEBOUNCE: 100,
            FOCUS_DEBOUNCE: 200,
            AJAX_TIMEOUT: 30000
        }
    };

    const DEFAULT_OPTIONS = {
        isClose: true,
        isRefresh: true,
        refreshCnt: 5,
        logActive: false,
        mbActive: false,
        mbMsg: '모바일 웹은 지원하지 않습니다.\nPC를 이용해 주세요',
        width: 500,
        height: 530,
        baseUrl: CONSTANTS.BASE_URL
    };

    const Utils = {
        getUserAgent() {
            return (navigator.userAgent || navigator.vendor || global.opera || '').toLowerCase();
        },

        isIOS() {
            return /iPad|iPhone|iPod/.test(navigator.userAgent);
        },

        isAndroid() {
            return /Android/.test(navigator.userAgent);
        },

        isFunction(value) {
            return typeof value === 'function';
        },

        isFunctionName(name) {
            return typeof global[name] === 'function';
        },

        getQueryParameter(name) {
            try {
                return new URLSearchParams(global.location.search).get(name) || null;
            } catch (error) {
                console.warn('URL parameter parsing error:', error);
                return null;
            }
        },

        safeJSONParse(value) {
            try {
                return JSON.parse(value);
            } catch (error) {
                console.warn('JSON parsing error:', error);
                return null;
            }
        },

        calculatePopupPosition(width, height) {
            const screenLeft = global.screenLeft || global.screenX || 0;
            const screenTop = global.screenTop || global.screenY || 0;
            const windowWidth = global.innerWidth || document.documentElement.clientWidth || screen.width;
            const windowHeight = global.innerHeight || document.documentElement.clientHeight || screen.height;

            return { left: Math.max(0, screenLeft + windowWidth / 2 - width / 2), top: Math.max(0, screenTop + windowHeight / 2 - height / 2) };
        },

        deepClone(value) {
            if (value === null || typeof value !== 'object') return value;
            if (value instanceof Date) return new Date(value.getTime());
            if (Array.isArray(value)) {
                return value.map(item => this.deepClone(item));
            }

            const cloned = {};
            Object.keys(value).forEach(key => {
                cloned[key] = this.deepClone(value[key]);
            });
            return cloned;
        },

        debounce(func, wait) {
            let timer = null;
            return (...args) => {
                clearTimeout(timer);
                timer = setTimeout(() => func(...args), wait);
            };
        },

        validateInput(value) {
            if (typeof value !== 'string') return false;
            return ![/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, /javascript:/gi, /on\w+\s*=/gi].some(pattern => pattern.test(value));
        },

        validateServerResponse(response, type) {
            const invalid = message => {
                const error = new Error(message);
                error.code = 'INVALID_SERVER_RESPONSE';
                throw error;
            };

            if (!response || typeof response !== 'object' || Array.isArray(response)) {
                invalid('서버 응답 형식이 유효하지 않습니다.');
            }

            if (typeof response.success !== 'boolean') {
                invalid('서버 응답의 success 값이 유효하지 않습니다.');
            }

            if (type === 'init' && response.success) {
                if (typeof response.sessionKey !== 'string' || !response.sessionKey) {
                    invalid('서버 응답에 sessionKey가 없습니다.');
                }

                if (typeof response.url !== 'string' || !response.url) {
                    invalid('서버 응답에 앱 연동 URL이 없습니다.');
                }
            }

            if (type === 'check' && response.success && response.returnUrl !== 'Failed' && response.returnData == null) {
                invalid('서버 응답에 로그인 결과가 없습니다.');
            }

            return response;
        },

        checkCSPPolicy() {
            try {
                const meta = document.querySelector('meta[http-equiv="Content-Security-Policy"]');
                if (meta && meta.content.includes('unsafe-inline')) console.warn('CSP policy may block dynamic form creation');
            } catch (error) {
                // CSP 점검 실패는 로그인 흐름을 중단하지 않는다.
            }
        }
    };

    class DIDLoginService {
        constructor() {
            this.options = Utils.deepClone(DEFAULT_OPTIONS);
            this.isInitialized = false;
            this.operationId = 0;
            this.activeControllers = new Set();

            this.lifecycleHandlers = null;
            this.iosAutoCheckScheduled = false;

            this.popupWindow = null;
            this.popupCheckTimer = null;
            this.messageHandler = null;

            this.sessionKey = null;
            this.mobilePoller = null;
            this.activityHandlers = null;
            this.immediateCheck = null;

            this.iosPoller = null;
        }

        reset() {
            this.cleanup();
            this.isInitialized = false;

            if (this.options.logActive) console.info('DIDLogin instance has been reset');
            return this;
        }

        init(options = {}) {
            try {
                this.setOptions(options);
                this.setupEventListeners();
                this.handleIOSAutoCheck();
                Utils.checkCSPPolicy();
                this.isInitialized = true;
                this.restorePendingMobileLogin();

                if (this.options.logActive) console.info(`DIDLogin library v${CONSTANTS.VERSION} initialized`);
                return this;
            } catch (error) {
                console.error('DIDLogin initialization failed:', error);
                throw error;
            }
        }

        setOptions(options = {}) {
            const previousMbActive = this.options.mbActive;
            const validOptions = Object.keys(DEFAULT_OPTIONS);

            Object.keys(options).forEach(key => {
                if (!validOptions.includes(key)) {
                    console.warn(`Invalid option: ${key}`);
                    return;
                }

                const value = options[key];
                if (key === 'baseUrl' && typeof value === 'string' && value.trim()) {
                    if (!Utils.validateInput(value)) {
                        console.warn(`Invalid baseUrl format: ${key}`);
                        return;
                    }
                    this.options[key] = value.trim().replace(/\/$/, '');
                    return;
                }

                if (typeof value !== typeof DEFAULT_OPTIONS[key]) {
                    console.warn(`Invalid type for option ${key}. Expected: ${typeof DEFAULT_OPTIONS[key]}, Got: ${typeof value}`);
                    return;
                }

                if (typeof value === 'string' && value.trim() === '' && key !== 'mbMsg') {
                    console.warn(`Option ${key} cannot be empty string`);
                    return;
                }
                this.options[key] = value;
            });

            if (this.isInitialized && !previousMbActive && this.options.mbActive) {
                this.handleIOSAutoCheck();
            }
        }

        setupEventListeners() {
            if (this.lifecycleHandlers) return;

            const beforeUnloadHandler = () => {
                if (this.sessionKey || this.mobilePoller || this.iosPoller) return;
                this.cleanup();
            };

            global.addEventListener('beforeunload', beforeUnloadHandler);
            this.lifecycleHandlers = { beforeUnloadHandler };
        }

        handleIOSAutoCheck() {
            if (!this.options.mbActive || !Utils.isIOS() || this.iosAutoCheckScheduled) {
                return;
            }

            this.iosAutoCheckScheduled = true;

            setTimeout(() => {
                this.iosAutoCheckScheduled = false;
                if (this.options.mbActive) this.iosCheckProcess();
            }, CONSTANTS.TIMING.IOS_AUTO_CHECK_DELAY);
        }

        startOperation() {
            this.operationId += 1;
            return this.operationId;
        }

        isCurrentOperation(operationId) {
            return operationId === this.operationId;
        }

        abortActiveRequests() {
            this.activeControllers.forEach(controller => controller.abort());
            this.activeControllers.clear();
        }

        async login(loginData) {
            if (!this.isInitialized) {
                if (this.options.logActive) console.warn('Instance not initialized, auto-initializing...');
                this.init();
            }

            let operationId = null;
            try {
                const validation = this.validateLoginData(loginData);
                if (!validation.valid) throw new Error(validation.message);

                this.cleanup();
                operationId = this.startOperation();

                if (Utils.isAndroid()) {
                    return await this.handleMobileLogin('android', loginData, operationId);
                }
                if (Utils.isIOS()) {
                    return await this.handleMobileLogin('ios', loginData, operationId);
                }
                return await this.handleWebLogin(loginData, operationId);
            } catch (error) {
                if (operationId !== null && !this.isCurrentOperation(operationId)) {
                    return false;
                }

                console.error('Login process failed:', error);
                alert(error.message || '로그인 처리 중 오류가 발생했습니다.');
                this.cleanup();
                return false;
            }
        }

        validateLoginData(data) {
            if (!data || typeof data !== 'object' || Array.isArray(data)) {
                return { valid: false, message: '로그인 데이터는 객체여야 합니다.' };
            }

            if (!data.flag) data.flag = CONSTANTS.FLAGS.PERSONAL;
            if (data.flag !== CONSTANTS.FLAGS.PERSONAL && data.flag !== CONSTANTS.FLAGS.CORPORATE) {
                return { valid: false, message: '지원하지 않는 인증 구분입니다.' };
            }

            const requiredFields = data.flag === CONSTANTS.FLAGS.CORPORATE ? ['siteId'] : ['siteId', 'requiredVC'];

            for (const field of requiredFields) {
                const value = data[field];
                if (value == null || typeof value !== 'string' || !value.trim()) {
                    return { valid: false, message: `${field}는 필수 입력사항입니다.` };
                }
                if (!Utils.validateInput(value)) {
                    return { valid: false, message: `${field}에 유효하지 않은 문자가 포함되어 있습니다.` };
                }
            }

            if (!data.returnUrl && !data.callbackFunc) {
                return { valid: false, message: 'returnUrl 또는 callbackFunc 중 하나는 필수입니다.' };
            }

            if (data.callbackFunc) {
                if (typeof data.callbackFunc === 'string' && !Utils.isFunctionName(data.callbackFunc)) {
                    return { valid: false, message: `콜백 함수 '${data.callbackFunc}'를 찾을 수 없습니다.` };
                }
                if (typeof data.callbackFunc !== 'string' && !Utils.isFunction(data.callbackFunc)) {
                    return { valid: false, message: '유효하지 않은 콜백 함수입니다.' };
                }
            }

            if (this.options.mbActive && Utils.isIOS()) {
                if (!data.iosCallbackFuncName) {
                    return { valid: false, message: '모바일 웹 로그인 지원 시 iosCallbackFuncName 는 필수 값입니다.' };
                }
                if (!Utils.isFunctionName(data.iosCallbackFuncName)) {
                    return { valid: false, message: `iOS 콜백 함수 '${data.iosCallbackFuncName}'를 찾을 수 없습니다.` };
                }
                if (Utils.isFunction(data.iosCallbackFuncName)) {
                    return { valid: false, message: 'iOS 콜백 함수는 함수 형태로 전달 할 수 없습니다. D클라우드 정책 위배.' };
                }
            }

            if (
                data.returnUrl &&
                (
                    typeof data.returnUrl !== 'string' ||
                    !Utils.validateInput(data.returnUrl)
                )
            ) {
                return { valid: false, message: 'returnUrl이 유효하지 않습니다.' };
            }
            return { valid: true };
        }

        async handleMobileLogin(os, loginData, operationId) {
            if (!this.options.mbActive) throw new Error(this.options.mbMsg);

            const success = await this.mobileWebInitProcess(os, loginData, operationId);
            return success && this.isCurrentOperation(operationId);
        }

        async handleWebLogin(loginData, operationId) {
            const url = `${this.options.baseUrl}/popup/v4/dqrLogin`;
            if (this.options.logActive) console.info('Opening login popup:', { url, flag: loginData.flag });

            loginData.origin = global.location.origin;
            this.openPopup(url, loginData, operationId);
            this.setupMessageListener(loginData, operationId);
            return true;
        }

        setupAppLaunchTimeout(launchCallback) {
            const startTime = Date.now();
            let hasLaunched = false;
            const visibilityHandler = () => {
                if (document.visibilityState === 'hidden') {
                    hasLaunched = true;
                    document.removeEventListener('visibilitychange', visibilityHandler);
                }
            };

            document.addEventListener('visibilitychange', visibilityHandler);
            setTimeout(() => {
                document.removeEventListener('visibilitychange', visibilityHandler);
                if (!hasLaunched && Date.now() - startTime < CONSTANTS.APP_LAUNCH_TIMEOUT) {
                    launchCallback();
                }
            }, CONSTANTS.APP_LAUNCH_DELAY);
        }

        openPopup(url, loginData, operationId) {
            const formData = { siteId: loginData.siteId, isClose: this.options.isClose, logActive: this.options.logActive, origin: loginData.origin, flag: loginData.flag };

            if (loginData.flag === CONSTANTS.FLAGS.PERSONAL) {
                formData.requiredVC = loginData.requiredVC;
                formData.subVC = loginData.subVC || '';
                formData.isRefresh = this.options.isRefresh;
                formData.refreshCnt = this.options.refreshCnt;
            }

            const form = this.createForm(url, formData, CONSTANTS.POPUP_TARGET);
            const position = Utils.calculatePopupPosition(this.options.width, this.options.height);
            const features = [`width=${this.options.width}`, `height=${this.options.height}`, `left=${position.left}`, `top=${position.top}`, 'resizable=no', 'scrollbars=no', 'menubar=no', 'toolbar=no', 'location=no', 'status=no'].join(',');

            this.popupWindow = global.open('', CONSTANTS.POPUP_TARGET, features);
            if (!this.popupWindow) {
                form.remove();
                throw new Error('팝업이 차단되었습니다. 브라우저의 팝업 차단을 해제해 주세요.');
            }

            try {
                this.popupWindow.document.title = loginData.flag === CONSTANTS.FLAGS.CORPORATE ? '법인 ID 로그인' : 'DID 로그인';
                this.popupWindow.document.body.setAttribute('role', 'dialog');
                this.popupWindow.document.body.setAttribute('aria-label', 'DID 로그인 창');
            } catch (error) {
                // 크로스 오리진 전환 이후 접근 실패는 무시한다.
            }

            form.submit();
            form.remove();
            this.monitorPopupClosure(operationId, this.popupWindow);
        }

        monitorPopupClosure(operationId, popupWindow) {
            const checkClosed = () => {
                if (!this.isCurrentOperation(operationId) || this.popupWindow !== popupWindow) {
                    return;
                }
                if (popupWindow.closed) {
                    if (this.options.logActive) console.info('Popup window was closed');
                    this.finishWebOperation();
                    return;
                }
                this.popupCheckTimer = setTimeout(checkClosed, CONSTANTS.TIMING.POPUP_CHECK_INTERVAL);
            };

            this.popupCheckTimer = setTimeout(checkClosed, CONSTANTS.TIMING.POPUP_CHECK_INTERVAL);
        }

        setupMessageListener(loginData, operationId) {
            if (this.messageHandler) {
                global.removeEventListener('message', this.messageHandler);
            }

            let handled = false;
            this.messageHandler = async event => {
                if (!this.isValidOrigin(event.origin)) {
                    console.warn('Message from unauthorized origin:', event.origin);
                    return;
                }
                if (event.source !== this.popupWindow) {
                    console.warn('Message from unauthorized window');
                    return;
                }
                if (handled || !this.isCurrentOperation(operationId)) {
                    return;
                }

                handled = true;
                this.cleanupWebResources();

                try {
                    await this.handleLoginResult(event.data, loginData);
                } catch (error) {
                    this.handleLoginResultError(error, 'Web');
                } finally {
                    if (this.isCurrentOperation(operationId)) {
                        this.finishWebOperation();
                    }
                }
            };
            global.addEventListener('message', this.messageHandler);
        }

        isValidOrigin(origin) {
            try {
                return origin === new URL(this.options.baseUrl).origin;
            } catch (error) {
                console.warn('Origin validation error:', error);
                return false;
            }
        }

        handleLoginResultError(error, context) {
            const detail = error && error.stack ? error.stack : String(error);

            if (error && error.code === 'DIDLOGIN_CALLBACK_ERROR') {
                console.error(`Login callback "${error.callbackName}" failed:\n${detail}`);
                this.showError('콜백 함수에서 오류가 발생했습니다.');
                return;
            }

            console.error(`${context} login result handling failed:\n${detail}`);
            this.showError('로그인 결과 처리 중 오류가 발생했습니다.');
        }

        async invokeLoginCallback(callback, callbackName, data) {
            try {
                await Promise.resolve(callback(data));
                return true;
            } catch (error) {
                const callbackError = error instanceof Error ? error : new Error(String(error));
                callbackError.code = 'DIDLOGIN_CALLBACK_ERROR';
                callbackError.callbackName = callbackName;
                throw callbackError;
            }
        }

        async handleLoginResult(data, loginData) {
            if (loginData.returnUrl) {
                return await this.goReturnUrl(data, loginData.returnUrl, loginData.callbackFunc);
            }

            if (typeof loginData.callbackFunc === 'string') {
                return await this.invokeLoginCallback(global[loginData.callbackFunc], loginData.callbackFunc, data);
            }

            if (Utils.isFunction(loginData.callbackFunc)) {
                return await this.invokeLoginCallback(loginData.callbackFunc, loginData.callbackFunc.name || '(anonymous)', data);
            }
            throw new Error('유효한 콜백 방식이 없습니다.');
        }

        createForm(action, data, target = null) {
            const form = document.createElement('form');
            form.setAttribute('charset', 'UTF-8');
            form.setAttribute('method', 'POST');
            form.setAttribute('action', action);
            if (target) form.setAttribute('target', target);
            form.style.display = 'none';

            Object.entries(data || {}).forEach(([name, value]) => {
                if (value == null) return;
                const input = document.createElement('input');
                input.type = 'hidden';
                input.name = name;
                input.value = String(value);
                form.appendChild(input);
            });
            document.body.appendChild(form);
            return form;
        }

        async mobileWebInitProcess(os, data, operationId) {
            const initUrl = `${this.options.baseUrl}/mobileWeb/login/init`;
            const initData = { siteId: data.siteId, flag: data.flag, origin: global.location.origin };
            if (data.flag === CONSTANTS.FLAGS.PERSONAL) {
                initData.requiredVC = data.requiredVC;
                initData.subVC = data.subVC || '';
            } else {
                alert('법인ID는 모바일 웹 로그인을 지원하지 않습니다.');
                return false;
            }

            if (this.options.logActive) console.info('Mobile web initialization:', { url: initUrl, siteId: data.siteId, flag: data.flag });

            try {
                if (!this.isCurrentOperation(operationId)) {
                    return false;
                }
                const response = Utils.validateServerResponse(
                    await this.postAjax(initUrl, initData),
                    'init'
                );
                if (!this.isCurrentOperation(operationId)) {
                    return false;
                }
                if (!response.success || response.errMsg) {
                    throw new Error(response.errMsg || '모바일 로그인 초기화에 실패했습니다.');
                }

                this.sessionKey = response.sessionKey;
                const mobileParam = this.buildMobileParam(response, data);
                let appUrl;

                if (os === 'android') {
                    data.sessionKey = response.sessionKey;
                    this.savePendingMobileLogin(data, operationId);
                    this.startAndroidCheck(data, operationId);
                    appUrl = `didapp://mobileweblogin?${mobileParam}`;
                } else {
                    const cleanUrl = new URL(global.location.href);

                    if (cleanUrl.searchParams.has('mwSessionKey')) {
                        ['mwSessionKey', 'siteId', 'rtnUrl', 'cbf'].forEach(name => cleanUrl.searchParams.delete(name));
                    }

                    const extendedParam = `${mobileParam}` +
                        `&siteId=${encodeURIComponent(data.siteId)}` +
                        `&rtnUrl=${encodeURIComponent(data.returnUrl || '')}` +
                        `&cbf=${encodeURIComponent(data.iosCallbackFuncName || '')}` +
                        `&returnUrl=${encodeURIComponent(cleanUrl.toString())}`;
                    appUrl = `didapp://mobileweblogin?${extendedParam}`;
                }

                this.setupAppLaunchTimeout(() => {
                    if (!this.isCurrentOperation(operationId)) return;
                    if (os === 'android') this.launchAndroidApp();
                    else this.launchIOSApp();
                });

                if (os === 'android') {
                    this.openAndroidAppUrl(appUrl);
                } else {
                    global.location.href = appUrl;
                }
                return true;
            } catch (error) {
                console.error('Mobile web initialization error:', error);
                throw error;
            }
        }

        buildMobileParam(response, data) {
            const params = new URLSearchParams({ sessionKey: response.sessionKey, flag: data.flag, url: response.url });
            if (data.flag === CONSTANTS.FLAGS.PERSONAL) {
                params.append('requiredVC', response.requiredVC);
                params.append('subVC', response.subVC || '');
            }
            return params.toString();
        }

        openAndroidAppUrl(appUrl) {
            const iframe = document.createElement('iframe');
            iframe.style.display = 'none';
            iframe.style.width = '0';
            iframe.style.height = '0';
            iframe.style.border = '0';
            iframe.src = appUrl;

            document.body.appendChild(iframe);

            setTimeout(() => {
                if (iframe.parentNode) {
                    iframe.parentNode.removeChild(iframe);
                }
            }, CONSTANTS.APP_LAUNCH_TIMEOUT);
        }

        getPendingMobileLoginKey() {
            return 'DIDLogin.pendingMobileLogin';
        }

        savePendingMobileLogin(data, operationId) {
            try {
                const pending = {
                    operationId,
                    sessionKey: data.sessionKey,
                    siteId: data.siteId,
                    origin: global.location.origin,
                    returnUrl: data.returnUrl || null,
                    callbackFunc: typeof data.callbackFunc === 'string' ? data.callbackFunc : null,
                    createdAt: Date.now()
                };

                sessionStorage.setItem(this.getPendingMobileLoginKey(), JSON.stringify(pending));
            } catch (error) {
                if (this.options.logActive) console.warn('Failed to save pending mobile login:', error);
            }
        }

        clearPendingMobileLogin() {
            try {
                sessionStorage.removeItem(this.getPendingMobileLoginKey());
            } catch (error) {
                if (this.options.logActive) console.warn('Failed to clear pending mobile login:', error);
            }
        }

        restorePendingMobileLogin() {
            if (!this.options.mbActive || !Utils.isAndroid() || this.mobilePoller) {
                return;
            }

            try {
                const pendingStr = sessionStorage.getItem(this.getPendingMobileLoginKey());
                if (!pendingStr) {
                    return;
                }

                const pending = JSON.parse(pendingStr);
                if (!pending || !pending.sessionKey || !pending.siteId || !pending.createdAt) {
                    this.clearPendingMobileLogin();
                    return;
                }

                if (Date.now() - pending.createdAt > CONSTANTS.TIMEOUT_DURATION) {
                    this.clearPendingMobileLogin();
                    return;
                }

                const operationId = this.startOperation();
                const data = {
                    sessionKey: pending.sessionKey,
                    siteId: pending.siteId,
                    returnUrl: pending.returnUrl,
                    callbackFunc: pending.callbackFunc
                };

                this.sessionKey = data.sessionKey;
                this.startAndroidCheck(data, operationId);
            } catch (error) {
                this.clearPendingMobileLogin();
                if (this.options.logActive) console.warn('Failed to restore pending mobile login:', error);
            }
        }

        classifyCheckResponse(response) {
            if (response.success && response.returnUrl !== 'Failed') {
                return 'success';
            }
            if (typeof response.msg === 'string' && response.msg.includes('폐기된 VC')) {
                return 'revoked';
            }
            if (response.returnUrl === 'Failed') return 'failed';
            return 'pending';
        }

        createAdaptivePoller(config) {
            let active = true;
            let timer = null;
            let expireTimer = null;
            let inFlight = false;
            let failures = 0;
            let interval = CONSTANTS.ADAPTIVE_POLLING.MIN_INTERVAL;

            const isActive = () => active && config.isActive();
            const isPaused = () => typeof config.isPaused === 'function' && config.isPaused();
            const clearTimer = () => {
                clearTimeout(timer);
                timer = null;
            };
            const stop = () => {
                active = false;
                clearTimer();
                clearTimeout(expireTimer);
                expireTimer = null;
            };
            const pause = () => {
                clearTimer();
            };
            const schedule = delay => {
                if (!isActive()) return;
                clearTimer();
                if (isPaused()) {
                    if (config.onPaused) config.onPaused();
                    return;
                }
                timer = setTimeout(() => {
                    timer = null;
                    check();
                }, delay);
            };

            const check = async () => {
                if (!isActive() || isPaused()) return false;
                if (inFlight) {
                    schedule(CONSTANTS.ADAPTIVE_POLLING.MIN_INTERVAL);
                    return false;
                }

                clearTimer();
                inFlight = true;
                let continuePolling = true;

                try {
                    const response = await config.request();
                    if (!isActive() || isPaused()) return false;

                    switch (this.classifyCheckResponse(response)) {
                        case 'success':
                            continuePolling = false;
                            stop();
                            await config.onSuccess(response);
                            return true;
                        case 'revoked':
                            continuePolling = false;
                            stop();
                            config.onRevoked(response);
                            return false;
                        case 'failed':
                            continuePolling = false;
                            stop();
                            config.onFailed(response);
                            return false;
                        default:
                            failures = 0;
                            interval = Math.max(CONSTANTS.ADAPTIVE_POLLING.MIN_INTERVAL, interval * 0.9);
                            if (config.onPending) config.onPending(response);
                    }
                } catch (error) {
                    if (!isActive()) return false;

                    if (error.code === 'INVALID_SERVER_RESPONSE') {
                        continuePolling = false;
                        stop();
                        config.onFailed({ msg: error.message });
                        return false;
                    }

                    failures += 1;
                    config.onAttemptError(error, failures);
                    interval = Math.min(interval * 1.5, CONSTANTS.ADAPTIVE_POLLING.MAX_INTERVAL);
                    if (failures >= CONSTANTS.ADAPTIVE_POLLING.MAX_FAILURES) {
                        continuePolling = false;
                        stop();
                        config.onMaxFailures(error);
                        return false;
                    }
                } finally {
                    inFlight = false;
                }

                if (continuePolling && isActive()) schedule(interval);
                return false;
            };

            const checkNow = () => {
                clearTimer();
                if (isPaused()) return false;
                return check();
            };

            expireTimer = setTimeout(() => {
                if (!isActive()) return;
                stop();
                config.onExpired();
            }, CONSTANTS.TIMEOUT_DURATION);
            schedule(config.initialDelay || CONSTANTS.ADAPTIVE_POLLING.DEBOUNCE_DELAY);

            return { stop, pause, checkNow };
        }

        startAndroidCheck(data, operationId) {
            const chkUrl = `${this.options.baseUrl}/mobileWeb/login/chk`;
            const chkData = { sessionKey: data.sessionKey, siteId: data.siteId, origin: global.location.origin };

            if (this.options.logActive) console.info('Starting mobile check:', { url: chkUrl, siteId: data.siteId });

            this.mobilePoller = this.createAdaptivePoller({
                request: async () => Utils.validateServerResponse(
                    await this.postAjax(chkUrl, chkData),
                    'check'
                ),
                isActive: () => this.isCurrentOperation(operationId),
                isPaused: () => document.visibilityState === 'hidden',
                initialDelay: CONSTANTS.ADAPTIVE_POLLING.MIN_INTERVAL,
                onPaused: null,
                onSuccess: async response => {
                    this.finishAndroidOperation();
                    try {
                        await this.handleLoginResult(response.returnData, data);
                    } catch (error) {
                        this.handleLoginResultError(error, 'Android');
                    }
                },
                onRevoked: () => {
                    this.finishAndroidOperation();
                    console.error('Revoked VC');
                    this.showError('폐기된 VC 입니다. 재발급 받고 다시 시도해 주세요.');
                },
                onFailed: response => {
                    this.finishAndroidOperation();
                    this.showError(response.msg || '로그인에 실패했습니다.');
                },
                onPending: null,
                onAttemptError: (error, failures) => {
                    if (this.options.logActive) console.warn(`Check attempt failed (${failures}/${CONSTANTS.ADAPTIVE_POLLING.MAX_FAILURES}):`, error.message);
                },
                onMaxFailures: () => {
                    this.finishAndroidOperation();
                    console.error('Max consecutive failures reached');
                    this.showError('서버 연결에 문제가 발생했습니다. 잠시 후 다시 시도해주세요.');
                },
                onExpired: () => {
                    this.finishAndroidOperation();
                    if (this.options.logActive) console.log('Session expired');
                    this.showError('로그인 세션이 만료되었습니다.');
                }
            });

            this.immediateCheck = () => this.mobilePoller.checkNow();
            this.setupUserActivityCheck();
        }

        setupUserActivityCheck() {
            const pauseMobileCheck = () => {
                if (this.mobilePoller) this.mobilePoller.pause();
            };

            const resumeMobileCheck = () => {
                setTimeout(() => {
                    if (
                        document.visibilityState !== 'hidden' &&
                        this.sessionKey &&
                        this.immediateCheck
                    ) {
                        this.immediateCheck();
                    }
                }, CONSTANTS.TIMING.VISIBILITY_DEBOUNCE);
            };

            const visibilityHandler = () => {
                if (document.visibilityState === 'hidden') {
                    pauseMobileCheck();
                    return;
                }

                resumeMobileCheck();
            };

            const focusHandler = () => {
                resumeMobileCheck();
            };

            const pageShowHandler = () => {
                resumeMobileCheck();
            };

            const pageHideHandler = () => {
                pauseMobileCheck();
            };

            document.addEventListener('visibilitychange', visibilityHandler);
            global.addEventListener('focus', focusHandler);
            global.addEventListener('pageshow', pageShowHandler);
            global.addEventListener('pagehide', pageHideHandler);
            this.activityHandlers = { visibilityHandler, focusHandler, pageShowHandler, pageHideHandler };
        }

        removeIOSCallbackParamsFromUrl() {
            try {
                const url = new URL(global.location.href);
                const parameterNames = ['mwSessionKey', 'siteId', 'rtnUrl', 'cbf'];

                let changed = false;

                parameterNames.forEach(name => {
                    if (url.searchParams.has(name)) {
                        url.searchParams.delete(name);
                        changed = true;
                    }
                });

                if (!changed) return true;

                global.history.replaceState(global.history.state, document.title, url.pathname + url.search + url.hash);

                if (this.options.logActive) console.info('iOS callback parameters removed from URL');

                return true;
            } catch (error) {
                if (this.options.logActive) console.warn('Failed to clean iOS callback URL:', error);

                return false;
            }
        }

        iosCheckProcess() {
            const mwSessionKey = Utils.getQueryParameter('mwSessionKey');
            const siteId = Utils.getQueryParameter('siteId');
            const returnUrl = Utils.getQueryParameter('rtnUrl');
            const callbackFunc = Utils.getQueryParameter('cbf');
            if (!mwSessionKey || !Utils.isIOS()) return;

            this.removeIOSCallbackParamsFromUrl();

            this.cleanup();
            const operationId = this.startOperation();
            const chkUrl = `${this.options.baseUrl}/mobileWeb/login/chk`;
            const chkData = { sessionKey: mwSessionKey, siteId, origin: global.location.origin };

            if (this.options.logActive) console.info('iOS check process');

            this.iosPoller = this.createAdaptivePoller({
                request: async () => Utils.validateServerResponse(
                    await this.postAjax(chkUrl, chkData),
                    'check'
                ),
                isActive: () => this.isCurrentOperation(operationId),
                onSuccess: async response => {
                    const loginData = { returnUrl: returnUrl !== 'undefined' ? returnUrl : null, callbackFunc: callbackFunc !== 'undefined' ? callbackFunc : null };
                    this.finishIOSOperation();
                    try {
                        await this.handleLoginResult(response.returnData, loginData);
                    } catch (error) {
                        this.handleLoginResultError(error, 'iOS');
                    }
                },
                onRevoked: () => {
                    this.finishIOSOperation();
                    console.error('iOS revoked VC');
                    this.showError('폐기된 VC입니다. 재발급 후 다시 시도해 주세요.');
                },
                onFailed: response => {
                    this.finishIOSOperation();
                    const message = response.msg || '로그인에 실패했습니다.';
                    console.error('iOS login failed:', message);
                    this.showError(message);
                },
                onPending: () => {
                    if (this.options.logActive) console.info('iOS login pending; scheduling next check');
                },
                onAttemptError: (error, failures) => {
                    if (this.options.logActive) console.warn(`iOS check attempt failed (${failures}/${CONSTANTS.ADAPTIVE_POLLING.MAX_FAILURES}):`, error.message);
                },
                onMaxFailures: () => {
                    this.finishIOSOperation();
                    console.error('iOS max consecutive failures reached');
                    this.showError('서버 연결에 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.');
                },
                onExpired: () => {
                    this.finishIOSOperation();
                    if (this.options.logActive) console.log('iOS login session expired');
                    this.showError('로그인 세션이 만료되었습니다.');
                }
            });
        }

        async requestJson(url, { headers, body, includeErrorBody = false, timeout = CONSTANTS.TIMING.AJAX_TIMEOUT }) {
            const controller = new AbortController();
            this.activeControllers.add(controller);
            let timedOut = false;
            const timeoutId = setTimeout(() => {
                timedOut = true;
                controller.abort();
            }, timeout);

            try {
                const response = await fetch(url, { method: 'POST', headers, body, signal: controller.signal });
                if (!response.ok) {
                    const errorBody = includeErrorBody ? await response.text().catch(() => 'Unknown error') : '';
                    throw new Error(`HTTP ${response.status}: ${response.statusText}` + (errorBody ? ` - ${errorBody}` : ''));
                }
                return await response.json();
            } catch (error) {
                if (error.name === 'AbortError' && timedOut) {
                    const timeoutError = new Error('서버 응답 시간이 초과되었습니다.');
                    timeoutError.code = 'TIMEOUT';
                    throw timeoutError;
                }
                throw error;
            } finally {
                clearTimeout(timeoutId);
                this.activeControllers.delete(controller);
            }
        }

        async postAjax(url, data) {
            try {
                const result = await this.requestJson(
                    url,
                    {
                        headers: {
                            'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
                        },
                        body: new URLSearchParams(data).toString()
                    }
                );

                if (this.options.logActive) {
                    const summary = result && typeof result === 'object'
                        ? { success: result.success, returnUrl: result.returnUrl, msg: result.msg, errMsg: result.errMsg }
                        : { type: typeof result };

                    console.log('AJAX response:', summary);
                }
                return result;
            } catch (error) {
                if (error instanceof TypeError) {
                    throw new Error('네트워크 연결을 확인해주세요.');
                }
                throw error;
            }
        }

        async goReturnUrl(data, url, callbackFunc) {
            if (!url) {
                throw new Error('리턴 URL이 제공되지 않았습니다.');
            }

            if (callbackFunc && callbackFunc !== 'undefined') {
                const requestOperationId = this.operationId;
                let result;
                try {
                    result = await this.requestJson(
                        url,
                        {
                            headers: {
                                'Content-Type': 'application/json; charset=UTF-8'
                            },
                            body: typeof data === 'string' ? data : JSON.stringify(data)
                        }
                    );
                    if (!this.isCurrentOperation(requestOperationId)) {
                        if (this.options.logActive) console.info('Return URL response ignored: stale operation');
                        return false;
                    }
                    if (this.options.logActive) console.log('Return URL response received');
                } catch (error) {
                    if (error.name === 'AbortError') {
                        if (this.options.logActive) console.info('Return URL request cancelled');
                        return false;
                    }
                    console.error('Return URL request error:', error);
                    this.showError(error.code === 'TIMEOUT' ? '서버 응답 시간이 초과되었습니다.' : '서버 통신 중 오류가 발생했습니다.');
                    return false;
                }

                if (typeof callbackFunc === 'string' && Utils.isFunctionName(callbackFunc)) {
                    return await this.invokeLoginCallback(global[callbackFunc], callbackFunc, result);
                } else if (Utils.isFunction(callbackFunc)) {
                    return await this.invokeLoginCallback(callbackFunc, callbackFunc.name || '(anonymous)', result);
                } else {
                    throw new Error('유효한 콜백 함수를 찾을 수 없습니다.');
                }
            }

            try {
                let dataObject = data;

                if (typeof data === 'string') {
                    try {
                        dataObject = JSON.parse(data);
                    } catch (error) {
                        throw new Error('로그인 결과 JSON 형식이 유효하지 않습니다.');
                    }
                }

                if (!dataObject || typeof dataObject !== 'object' || Array.isArray(dataObject)) {
                    throw new Error('로그인 결과는 객체여야 합니다.');
                }

                const form = this.createForm(url, dataObject);
                form.submit();
                return true;
            } catch (error) {
                console.error('Return URL form submit error:', error);
                this.showError('로그인 결과 전송 중 오류가 발생했습니다.');
                return false;
            }
        }

        launchAndroidApp() {
            const userAgent = Utils.getUserAgent();
            const installUrl = userAgent.includes('chrome') ? 'intent://didapp://launch#Intent;' + `scheme=didapp;package=${CONSTANTS.ANDROID_PACKAGE};end;` : 'market://details?id=' + CONSTANTS.ANDROID_PACKAGE;
            global.location.replace(installUrl);
        }

        launchIOSApp() {
            global.location.replace(`itms-apps://itunes.apple.com/app/id${CONSTANTS.IOS_APP_ID}`);
        }

        showError(message) {
            alert(message);
        }

        invalidateOperation() {
            this.operationId += 1;
            this.abortActiveRequests();
        }

        cleanupWebResources() {
            clearTimeout(this.popupCheckTimer);
            this.popupCheckTimer = null;

            if (this.popupWindow) {
                if (!this.popupWindow.closed) {
                    try {
                        this.popupWindow.close();
                    } catch (error) {
                        if (this.options.logActive) console.warn('Failed to close login popup:', error);
                    }
                }
                this.popupWindow = null;
            }

            if (this.messageHandler) {
                global.removeEventListener('message', this.messageHandler);
                this.messageHandler = null;
            }
        }

        cleanupAndroidResources() {
            if (this.mobilePoller) {
                this.mobilePoller.stop();
                this.mobilePoller = null;
            }
            if (this.activityHandlers) {
                document.removeEventListener('visibilitychange', this.activityHandlers.visibilityHandler);
                global.removeEventListener('focus', this.activityHandlers.focusHandler);
                global.removeEventListener('pageshow', this.activityHandlers.pageShowHandler);
                global.removeEventListener('pagehide', this.activityHandlers.pageHideHandler);
                this.activityHandlers = null;
            }
            this.sessionKey = null;
            this.immediateCheck = null;
        }

        cleanupIOSResources() {
            if (this.iosPoller) {
                this.iosPoller.stop();
                this.iosPoller = null;
            }
        }

        finishWebOperation() {
            this.invalidateOperation();
            this.cleanupWebResources();
        }

        finishAndroidOperation() {
            this.invalidateOperation();
            this.clearPendingMobileLogin();
            this.cleanupAndroidResources();
        }

        finishIOSOperation() {
            this.invalidateOperation();
            this.cleanupIOSResources();
        }

        cleanup() {
            this.invalidateOperation();
            this.cleanupWebResources();
            this.cleanupAndroidResources();
            this.cleanupIOSResources();
        }

        getVersion() {
            return CONSTANTS.VERSION;
        }

        getOptions() {
            return Utils.deepClone(this.options);
        }

        isReady() {
            return this.isInitialized;
        }
    }

    const didLoginInstance = new DIDLoginService();
    const DIDLogin = {
        init(options) {
            didLoginInstance.init(options);
            return this;
        },
        login(loginData) {
            return didLoginInstance.login(loginData);
        },
        loginPersonal(loginData) {
            if (loginData && typeof loginData === 'object') loginData.flag = CONSTANTS.FLAGS.PERSONAL;
            return didLoginInstance.login(loginData);
        },
        loginCorporate(loginData) {
            if (loginData && typeof loginData === 'object') loginData.flag = CONSTANTS.FLAGS.CORPORATE;
            return didLoginInstance.login(loginData);
        },
        setOptions(options) {
            didLoginInstance.setOptions(options);
            return this;
        },
        reset() {
            didLoginInstance.reset();
            return this;
        },
        version: CONSTANTS.VERSION,
        getVersion() {
            return didLoginInstance.getVersion();
        },
        getOptions() {
            return didLoginInstance.getOptions();
        },
        isReady() {
            return didLoginInstance.isReady();
        },
        cleanup() {
            didLoginInstance.cleanup();
            return this;
        }
    };

    global.DIDLogin = DIDLogin;
    global.didLogin = {
        option: didLoginInstance.options,
        setOption(options) {
            didLoginInstance.setOptions(options);
        },
        isReady() {
            return didLoginInstance.isReady();
        },
        loginPopup(loginData) {
            if (loginData && typeof loginData === 'object' && !loginData.flag) {
                loginData.flag = CONSTANTS.FLAGS.PERSONAL;
            }
            if (!didLoginInstance.isInitialized) {
                didLoginInstance.reset();
                didLoginInstance.init();
            }
            return didLoginInstance.login(loginData);
        }
    };

    if (typeof define === 'function' && define.amd) {
        define(function() {
            return DIDLogin;
        });
    } else if (typeof module !== 'undefined' && module.exports) {
        module.exports = DIDLogin;
    }

    const dispatchReady = () => {
        if (!didLoginInstance.isInitialized) {
            didLoginInstance.init();
        }
        global.dispatchEvent(new CustomEvent('didlogin:ready', { detail: DIDLogin }));
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', dispatchReady);
    } else {
        setTimeout(dispatchReady, 0);
    }
})(typeof window !== 'undefined' ? window : this);
