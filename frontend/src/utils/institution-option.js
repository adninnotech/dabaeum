/**
 * 기관 select 옵션. 이름이 같은 기관(테스트 기관 여럿 등)은 기관 코드를 붙여 구분한다 — 이름이 유일하면 이름만.
 * items: Institution[] (id·name·institutionCode) 또는 MyInstitution[] (institutionId·name)
 */
export function toInstitutionOptions(items) {
  const list = Array.isArray(items) ? items : [];
  const nameCount = list.reduce((counts, item) => ({ ...counts, [item.name]: (counts[item.name] || 0) + 1 }), {});
  return list.map((item) => {
    const duplicated = nameCount[item.name] > 1 && item.institutionCode;
    return {
      label: duplicated ? `${item.name} (${item.institutionCode})` : item.name,
      value: item.id || item.institutionId,
    };
  });
}
