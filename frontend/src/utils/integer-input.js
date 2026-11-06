/** 숫자 입력칸(type=number)의 정수 검사. 백엔드는 정수 칸에 온 소수를 400 으로 거부한다(예전엔 조용히 버림) */
export const INTEGER_REQUIRED = '정수로 입력해 주세요.';

/** 비어 있으면 '' (기본값은 화면이 채운다). 소수·숫자 아님이면 안내 문구 */
export function integerInputError(value, { min } = {}) {
  if (value === '' || value === null || value === undefined) return '';
  const number = Number(value);
  if (!Number.isInteger(number)) return INTEGER_REQUIRED;
  if (min !== undefined && number < min) return `${min} 이상 정수로 입력해 주세요.`;
  return '';
}
