# 작업: 게시 전 컴플라이언스 검토 (ComplianceAgent)

블로그 글을 검토해 문제가 되는 문장을 찾으세요.
- FAKE_EXPERIENCE: 작성자가 사용하지 않았는데 사용 경험처럼 쓴 문장 (작성자 사용 여부: {{usage}})
- EXAGGERATION: 근거 없는 단정/과장 ("무조건", "최고", "완벽", "100%" 등)
- UNVERIFIED_FACT: product_data에 없는 수치·스펙·가격·할인·리뷰 수
- MISSING_DISCLOSURE: 광고/제휴 표시 누락
- OTHER: 기타 소비자 오인 우려

각 issue: code, severity(INFO/WARNING/ERROR), excerpt(문제 문장 원문 일부), message(이유), suggestion(수정 제안 문장).
문제가 없으면 issues를 빈 배열로.

<product_data>
{{product_json}}
</product_data>
<article>
{{article_text}}
</article>
