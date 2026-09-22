# 작업: 블로그 시각 자료 기획 (VisualStrategyAgent)

블로그 글 구조를 보고 각 섹션에 어울리는 이미지 카드를 설계하세요.
카드는 앱이 직접 그리는 그래픽이며 실물 사진처럼 보이게 만들지 않습니다.

- visualTheme: 전체 비주얼 방향 한 문장
- stylePreset: CLEAN / SOFT / PREMIUM / TECH / CUTE / MINIMAL / DARK 중 상품 성격에 맞는 것 (사용자 지정: {{style_hint}})
- cards: 5~7장. type은 HERO, FEATURE, SPEC, PROS, CHECK, TARGET, CTA 중 하나.
  - HERO: title=상품명 요약(25자 이내), subtitle=핵심 한 줄(30자 이내)
  - FEATURE: title 예 "핵심 특징 3가지", items 3개 (각 25자 이내)
  - SPEC: title만 작성 (표는 앱이 검증된 데이터로 채움), items는 빈 배열
  - PROS: 장점 items 3~4개
  - CHECK: 구매 전 확인 items 3~4개
  - TARGET: "이런 분께 잘 맞아요" items 3개 (데이터로 도출 가능한 수준)
  - CTA: title=행동 유도 한 줄, cta=버튼 문구(12자 이내)
  - afterSection: 이 카드를 넣을 섹션 번호(0부터, 아래 목록 기준). HERO는 -1.
  - imageIndex: 사용할 상품 이미지 번호(0부터), 없으면 -1.
- 검증되지 않은 수치나 사실을 카드에 쓰지 마세요.

섹션 목록:
{{sections}}

상품 이미지 수: {{image_count}}

<product_data>
{{product_json}}
</product_data>
