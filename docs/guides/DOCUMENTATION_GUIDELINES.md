# Documentation Guidelines

## Language Separation Rule

All documentation in this project must be written in separate files for each language:
- English version: No suffix (e.g., `README.md`)
- Korean version: `_ko` suffix (e.g., `README_ko.md`)

Both versions must be kept in sync at all times.

### File Naming Convention

- English (primary): `filename.md`
- Korean: `filename_ko.md`

Examples:
- `README.md` (English)
- `README_ko.md` (Korean)
- `temporal-sdk-comparison.md` (English)
- `temporal-sdk-comparison_ko.md` (Korean)

### Documentation Structure

1. **Separate Files**
   - Each language has its own dedicated file
   - No mixed-language content in a single file
   - Cleaner version control and easier maintenance

2. **Content Organization**
   - Each file contains content in only one language
   - Code examples remain in English in both versions
   - Comments in code can be translated in the Korean version

3. **File Synchronization**
   - When updating the English version, update the Korean version immediately
   - Use consistent structure and sections across both versions
   - Maintain technical accuracy in both languages

### Update Policy

1. **Simultaneous Updates**
   - Changes to English documentation require corresponding Korean updates
   - PRs must include both language versions when modifying documentation

2. **Review Process**
   - Documentation changes require review of both language versions
   - Ensure technical accuracy and consistency

3. **Translation Quality**
   - Maintain technical accuracy in both languages
   - Use consistent terminology (see glossary below)
   - Preserve code functionality and examples

### Common Terms Glossary

| English | Korean | Context |
|---------|--------|---------|
| Worker | 워커 | Temporal worker process |
| Workflow | 워크플로우 | Temporal workflow |
| Activity | 액티비티 | Temporal activity |
| Task Queue | 태스크 큐 | Temporal task queue |
| Document Processing | 문서 처리 | Document pipeline |
| Resource-Optimized | 리소스 최적화 | Resource allocation |
| CPU-bound | CPU 집약적 | CPU-intensive tasks |
| GPU-bound | GPU 집약적 | GPU-intensive tasks |
| IO-bound | IO 집약적 | IO-intensive tasks |
| Pipeline | 파이프라인 | Processing pipeline |
| Embedding | 임베딩 | Vector embedding |
| Vector Database | 벡터 데이터베이스 | Vector DB |
| Orchestration | 오케스트레이션 | Workflow orchestration |
| Scalability | 확장성 | System scalability |
| Fault Tolerance | 장애 허용 | Error resilience |
| Retry Policy | 재시도 정책 | Retry configuration |
| Timeout | 타임아웃 | Time limit |
| Batch Processing | 배치 처리 | Batch operations |
| Concurrency | 동시성 | Concurrent execution |
| Rate Limiting | 속도 제한 | Request rate control |

### Compliance Checklist

Before committing documentation:

- [ ] English version exists and is complete
- [ ] Korean version exists with `_ko` suffix
- [ ] Content is technically accurate in both languages
- [ ] File structure and sections match between versions
- [ ] Code examples work correctly
- [ ] Terms match the glossary
- [ ] No mixed languages in single file

### Migration from Mixed-Language Files

When encountering files with mixed English/Korean content:
1. Extract English content to the base filename (no suffix)
2. Extract Korean content to `filename_ko.md`
3. Ensure both files are complete and properly formatted
4. Remove the original mixed-language file