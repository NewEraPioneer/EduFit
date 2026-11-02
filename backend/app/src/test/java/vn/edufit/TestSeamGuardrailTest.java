package vn.edufit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Chặn tái phát "Test-Induced Design Damage" trong Spring bean của production.
 *
 * <ul>
 *   <li>Mỗi bean có tối đa một constructor public: không có constructor phụ truyền {@code null}
 *       hay collaborator rỗng để test né quy tắc nghiệp vụ.</li>
 *   <li>Không có field public có thể gán lại: test không được sửa trạng thái của bean.</li>
 *   <li>Không inject tùy chọn bằng {@code @Autowired(required = false)}: thiếu collaborator nghiệp vụ
 *       (audit, IAM...) phải làm ứng dụng không khởi động được thay vì âm thầm bỏ qua.</li>
 * </ul>
 */
@AnalyzeClasses(packages = "vn.edufit", importOptions = {ImportOption.DoNotIncludeTests.class})
public class TestSeamGuardrailTest {

  @ArchTest
  static final ArchRule spring_beans_have_at_most_one_public_constructor = classes()
      .that().areMetaAnnotatedWith(Component.class)
      .and().areNotInterfaces()
      .should(new ArchCondition<JavaClass>("have at most one public constructor") {
        @Override public void check(JavaClass bean, ConditionEvents events) {
          long publicConstructors = bean.getConstructors().stream()
              .filter(constructor -> constructor.getModifiers().contains(JavaModifier.PUBLIC))
              .count();
          if (publicConstructors > 1) {
            events.add(SimpleConditionEvent.violated(bean,
                bean.getName() + " có " + publicConstructors + " constructor public"));
          }
        }
      }).allowEmptyShould(false);

  @ArchTest
  static final ArchRule spring_beans_have_no_mutable_public_fields = classes()
      .that().areMetaAnnotatedWith(Component.class)
      .should(new ArchCondition<JavaClass>("not expose mutable public fields") {
        @Override public void check(JavaClass bean, ConditionEvents events) {
          for (JavaField field : bean.getFields()) {
            var modifiers = field.getModifiers();
            if (modifiers.contains(JavaModifier.PUBLIC) && !modifiers.contains(JavaModifier.FINAL)) {
              events.add(SimpleConditionEvent.violated(field, field.getFullName() + " là field public có thể gán lại"));
            }
          }
        }
      }).allowEmptyShould(false);

  @ArchTest
  static final ArchRule spring_beans_have_no_optional_collaborators = classes()
      .that().areMetaAnnotatedWith(Component.class)
      .should(new ArchCondition<JavaClass>("not inject collaborators with @Autowired(required = false)") {
        @Override public void check(JavaClass bean, ConditionEvents events) {
          bean.getConstructors().forEach(constructor -> constructor.getParameters().forEach(parameter ->
              parameter.tryGetAnnotationOfType(Autowired.class)
                  .filter(autowired -> !autowired.required())
                  .ifPresent(autowired -> events.add(SimpleConditionEvent.violated(parameter,
                      constructor.getFullName() + " inject tùy chọn tham số " + parameter.getIndex())))));
        }
      }).allowEmptyShould(false);
}
