# Contributing to PDF Reader

Thank you for your interest in contributing to the open-source PDF Reader project! This document provides guidelines and instructions for developers who want to help improve the application.

---

## Code of Conduct

By participating in this project, you agree to abide by our Code of Conduct:
- Be respectful and inclusive
- Provide constructive feedback
- No harassment, discrimination, or hostility
- Report violations to the maintainers

---

## Getting Started

### 1. Fork and Clone
```bash
git clone https://github.com/YOUR_USERNAME/pdf-reader.git
cd pdf-reader
git remote add upstream https://github.com/ORIGINAL_OWNER/pdf-reader.git
```

### 2. Create a Feature Branch
```bash
git checkout -b feature/your-feature-name
```

Follow branch naming conventions:
- `feature/feature-name` - New features
- `bugfix/bug-name` - Bug fixes
- `chore/task-name` - Maintenance tasks
- `docs/doc-name` - Documentation updates

### 3. Set Up Development Environment
```bash
./scripts/setup-dev-env.sh
```

---

## Development Workflow

### Before You Start
1. Check existing issues and PRs to avoid duplicate work
2. For major features, open an issue for discussion first
3. Ensure your work aligns with the PROJECT_ROADMAP.md phases

### Code Style

#### Kotlin
- Follow [Kotlin style guide](https://kotlinlang.org/docs/coding-conventions.html)
- Run `./gradlew ktlintFormat` to auto-format
- Use meaningful variable names
- Add KDoc comments for public APIs

#### C++ (JNI)
- Follow Google C++ style guide
- Use meaningful variable names
- Add comments for complex logic
- Run `clang-format` for formatting

#### Testing
- Write unit tests for new business logic
- Write instrumented tests for UI changes
- Aim for > 80% coverage on critical paths
- Run `./gradlew test connectedAndroidTest`

### Committing

Write clear, descriptive commit messages:
```
[FEATURE] Add stylus pressure sensitivity

- Implement pressure-sensitive strokes on annotation layer
- Add pressure normalization across Android devices
- Update rendering pipeline to handle pressure values
- Add unit tests for pressure interpolation

Fixes #123
```

Use conventional commits format:
- `[FEATURE]` - New features
- `[BUGFIX]` - Bug fixes
- `[CHORE]` - Maintenance
- `[DOCS]` - Documentation
- `[TEST]` - Test improvements
- `[PERF]` - Performance improvements

---

## Pull Request Process

### Before Submitting
1. **Rebase on latest main:**
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Run all checks locally:**
   ```bash
   ./gradlew build ktlint test
   ```

3. **Verify performance targets:**
   - Check section 9 of PROJECT_ROADMAP.md
   - Profile critical paths if applicable

### PR Template
```markdown
## Description
Brief summary of changes

## Related Issues
Fixes #123

## Changes Made
- Change 1
- Change 2
- Change 3

## Testing
- [ ] Unit tests added/updated
- [ ] Instrumented tests added/updated
- [ ] Tested on phone (API 26+)
- [ ] Tested on tablet (if applicable)

## Performance Impact
- First page render: X ms
- Stylus latency: X ms (if applicable)

## Screenshots/Videos
(If UI changes)

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-review completed
- [ ] Comments added for complex logic
- [ ] Documentation updated
- [ ] No breaking changes
```

### Review Process
1. **Automated checks:**
   - GitHub Actions build and tests pass
   - Code coverage maintained
   - Linting passes

2. **Manual review:**
   - At least one maintainer approval required
   - Community feedback encouraged
   - Constructive discussion on changes

3. **Addressing feedback:**
   - Push updates to the same branch
   - Avoid force-pushing after review starts (unless requested)
   - Mark conversations as resolved when addressed

---

## Testing Guidelines

### Unit Tests
```kotlin
@Test
fun testTextBoxCalculation() {
    val result = calculateTextBox(startX, startY, endX, endY)
    assertEquals(expected, result)
}
```

### Instrumented Tests
```kotlin
@RunWith(AndroidJUnit4::class)
class PdfRenderingTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)
    
    @Test
    fun testPageRenderLatency() {
        // Measure render performance
    }
}
```

### Performance Testing
- Use Android Profiler (CPU, Memory, GPU)
- Test with large PDFs (500MB+)
- Monitor frame time during animations
- Check for memory leaks (AddressSanitizer)

---

## JNI Development Guidelines

### Memory Management
- Properly release all JNI references
- Use RAII patterns for native resources
- Test with AddressSanitizer: `address`
- Check for null pointers at JNI boundaries

### Error Handling
```cpp
// Always check for exceptions after JNI calls
if (env->ExceptionCheck()) {
    env->ExceptionDescribe();
    return -1;
}
```

### Documentation
- Document C++ function signatures clearly
- Add comments explaining complex calculations
- Include example usage in KDoc

---

## Documentation

### README.md Updates
- Update features list when adding new capabilities
- Add screenshots for significant UI changes
- Keep build instructions current

### Architecture Documentation
- Update ARCHITECTURE.md for structural changes
- Create ADRs (Architecture Decision Records) for major decisions
- Document performance considerations

### Code Comments
- Explain "why" not "what"
- Use clear language
- Update when code changes

---

## Reporting Issues

### Bug Reports
Include:
- Android version and device model
- App version
- Steps to reproduce
- Expected vs actual behavior
- Screenshots/logs if applicable
- Stack trace (if crash)

### Feature Requests
Include:
- Clear description of the feature
- Use cases and benefit
- Alignment with PROJECT_ROADMAP.md phases
- Potential implementation approach (optional)

---

## Licensing

By contributing, you agree that your contributions will be licensed under the AGPL v3 license. You represent that you have the right to grant this license.

---

## Questions?

- Check existing issues and discussions
- Open a new discussion for general questions
- Contact maintainers for sensitive matters

**Thank you for contributing!** 🎉
