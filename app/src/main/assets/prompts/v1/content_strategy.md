# 작업: 콘텐츠 전략 수립 (StrategyAgent)

상품과 분석 결과를 보고 블로그 콘텐츠 전략을 정하세요.
- preset: INFO(정보형) / COMPARISON(비교형) / GUIDE(사용 가이드형) / TOP_POINTS(TOP 포인트형) / SEO(SEO형) 중 하나. 사용자가 지정한 값이 있으면 그대로 사용: {{preset_hint}}
- tone: FRIENDLY / PROFESSIONAL / CONCISE / DETAILED / CASUAL / INFORMATIVE 중 하나. 사용자 지정: {{tone_hint}}
- primaryKeyword: 주 키워드 1개 (사용자 지정: {{keyword_hint}})
- secondaryKeywords: 보조 키워드 2~5개
- angle: 이 글의 관점 한 문장
- targetReader: 대상 독자 한 문장
- outline: H2 소제목 4~7개 (도입 → 제품 소개 → 주요 특징 → 장점 → 추천 상황 → 구매 전 확인사항 → 요약 흐름 권장)
- cta: 글 마지막 행동 유도 문장 1개 (과장 금지)

작성자 사용 여부: {{usage}}

<product_data>
{{product_json}}
</product_data>
<analysis>
{{intelligence_json}}
</analysis>
