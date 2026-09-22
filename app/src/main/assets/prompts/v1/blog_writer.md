# 작업: 네이버 블로그 본문 작성 (WriterAgent)

전략에 맞춰 모바일에서 읽기 쉬운 블로그 본문을 작성하세요.

## 형식 규칙
- sections 배열에 순서대로 블록을 넣습니다. type은 h2, paragraph, list, quote 중 하나.
- paragraph 하나는 2~4문장, 180자 이내로 짧게. 긴 내용은 여러 paragraph로 나눕니다.
- 강조가 필요하면 **굵게** 표기를 문단당 1회 이하로 사용합니다.
- list는 items 배열에 3~6개 항목.
- 전략의 outline 순서를 따라 h2 소제목을 배치합니다.
- 목표 분량: 약 {{target_chars}}자.
- 말투: {{tone}}. 콘텐츠 유형: {{preset}}.
- 주 키워드 "{{primary_keyword}}"는 자연스럽게 3~5회, 보조 키워드는 1~2회만. 키워드 나열 금지.
- 가격/할인은 product_data에 있을 때만 언급하고 "작성 시점 기준"이라고 밝힙니다.
- 링크, 광고 표시 문구, 상품 정보 요약표는 앱이 자동으로 추가하므로 본문에 쓰지 마세요.
- hook: 글 첫머리에 들어갈 1~2문장 도입부.
- hashtags: 해시태그 5~10개 (# 없이 단어만).
- usedFacts: 본문에서 사용한 상품 사실(가격, 스펙 등)을 원문 값 그대로 나열.

## 사용 경험 규칙
작성자 사용 여부: {{usage}}
{{usage_rule}}

## 반복 방지
다음은 작성자가 이전 글에서 자주 쓴 표현입니다. 가능한 한 다른 표현을 쓰세요:
{{avoid_phrases}}

## 브랜드 프로필
{{brand_profile}}

{{custom_instruction}}

<strategy>
{{strategy_json}}
</strategy>
<analysis>
{{intelligence_json}}
</analysis>
<product_data>
{{product_json}}
</product_data>
