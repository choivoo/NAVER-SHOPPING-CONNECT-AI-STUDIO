# 작업: 숏폼 스토리보드 (ShortformAgent)

{{duration}}초 길이의 9:16 쇼핑 숏폼 스토리보드를 만드세요. 템플릿: {{template}}.

## 구조 가이드 (30초 기준, 길이에 비례해 조정)
0-2초 Hook → 2-6초 제품 소개 → 6-13초 핵심 특징 1 → 13-20초 핵심 특징 2 → 20-25초 누구에게 적합한지 → 25-30초 CTA

## 규칙
- scenes: 5~10개. 각 scene의 durationSec 합계가 {{duration}}초에 가깝게.
- onScreenText: 화면 큰 글자, 16자 이내.
- narration: 한국어 내레이션. 초당 약 {{syllables_per_sec}}음절 기준으로 durationSec에 맞는 길이. 문장은 짧게.
- transition: NONE, FADE, CROSS_FADE, SLIDE_LEFT, SLIDE_UP, ZOOM_IN, ZOOM_OUT, WIPE, FLASH 중. 과도하게 섞지 말고 템플릿 성격에 맞춰 1~2종류만 반복.
- motion: KEN_BURNS_IN, KEN_BURNS_OUT, PAN_LEFT, PAN_RIGHT, PULSE, NONE 중.
- imageIndex: 사용할 상품 이미지 번호(0부터, 이미지 수 {{image_count}}), 카드 그래픽이 나을 때는 -1.
- hooks: 첫 2초 훅 후보 5개, type은 PROBLEM, CURIOSITY, COMPARISON, BENEFIT, QUESTION 각 1개.
- bgmMood: UPBEAT, MINIMAL, TECH, CUTE, PREMIUM, CALM 중.
- thumbnailTexts: 썸네일 큰 글자 후보 5개 (12자 이내).
- 사용 경험 규칙: 작성자 사용 여부 {{usage}}. 사용하지 않았다면 사용 후기처럼 말하지 마세요.
- 가격은 product_data에 있을 때만, "작성 시점 기준"을 붙여서.

<product_data>
{{product_json}}
</product_data>
<article_summary>
{{article_summary}}
</article_summary>
