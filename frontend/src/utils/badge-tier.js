/*
 * 단계 배지(TIERED) 하나를 단계별 카드로 푼다 — 씨앗·새싹·…·나눔을 각각 보유/미보유로 보이려고.
 * 받은 단계보다 낮은 단계 = 보유, 받은 단계 = 보유 중(현재), 높은 단계 = 미보유.
 * 폐기된 배지는 어느 단계도 보유가 아니다(폐기 표시는 폐기 당시 단계 카드에만).
 */

/** 단계 하나의 조건 문장 — "강좌 6건 이상 이수" */
export function tierCriteriaText(level) {
  return `강좌 ${level.minCount}건 이상 이수`;
}

/**
 * @param {object} source { definitionId, name, imageUrl, levels[] }
 * @param {object|null} badge 받은 배지(ISSUED) — tierLevel 을 본다
 * @param {object|null} revoked 폐기된 배지
 * @returns {Array} [{ key, definitionId, level, name, groupName, imageUrl, owned, current, revoked, criteria, badge }]
 */
export function expandTierLevels(source, badge = null, revoked = null) {
  const heldLevel = badge?.tierLevel || 0;
  return (source.levels || []).map((level, index) => {
    const no = level.level || index + 1;
    return {
      key: `${source.definitionId}:${no}`,
      definitionId: source.definitionId,
      level: no,
      name: level.name,
      groupName: source.name,
      imageUrl: level.imageUrl || source.imageUrl || '',
      owned: heldLevel >= no,
      current: heldLevel === no,
      revoked: !badge && revoked?.tierLevel === no,
      criteria: tierCriteriaText(level),
      badge: badge || revoked || null,
    };
  });
}

/** 풀어서 센 보유 수·전체 수 — 단계 배지는 단계 수만큼 센다 */
export function countTierAware(items) {
  return items.reduce(
    (acc, item) => ({ owned: acc.owned + (item.owned ? 1 : 0), total: acc.total + 1 }),
    { owned: 0, total: 0 },
  );
}
