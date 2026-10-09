package com.hotelpms.billing.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * Every state-changing endpoint of billing-service must make an explicit authorization decision.
 *
 * <p>The gateway lets RECEPTIONIST through on {@code /api/v1/invoices/**}, so a method without
 * {@code @PreAuthorize} is callable by the whole front desk. A new write endpoint added without
 * a rule (for example one meant to be internal-only, which needs
 * {@code @PreAuthorize(INTERNAL_ONLY)}) would silently stay open. This rule turns "I forgot" into
 * a failing build: either annotate the method, or add it to {@link #OPEN_TO_THE_FRONT_DESK} with
 * the reason it is open.
 */
@AnalyzeClasses(packages = "com.hotelpms.billing", importOptions = ImportOption.DoNotIncludeTests.class)
final class EndpointAuthorizationArchTest {

    @ArchTest
    static final ArchRule WRITE_ENDPOINTS_MUST_DECLARE_AUTHORIZATION = methods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .should(declareAuthorizationWhenTheyChangeState());

    /**
     * Write endpoints deliberately open to every operational role, as {@code Class.method}.
     * Adding to this list is a security decision: say why next to the entry.
     */
    private static final Set<String> OPEN_TO_THE_FRONT_DESK = Set.of(
            // AddChargeModal / InvoiceDetailModal: the front desk adds and removes manual EXTRA charges.
            // The EXTRA-only rule for public callers is enforced in InvoiceServiceImpl.
            "InvoiceController.addCharge",
            "InvoiceController.removeCharge",
            // The front desk registers the payment that unblocks check-out (docs/USER_MANUAL.md).
            "PaymentController.addPayment");

    private EndpointAuthorizationArchTest() {
    }

    private static ArchCondition<JavaMethod> declareAuthorizationWhenTheyChangeState() {
        return new ArchCondition<>("have @PreAuthorize (or be listed as open to the front desk) when they change state") {
            @Override
            public void check(final JavaMethod method, final ConditionEvents events) {
                if (!changesState(method) || hasPreAuthorize(method)) {
                    return;
                }
                final String id = method.getOwner().getSimpleName() + "." + method.getName();
                if (!OPEN_TO_THE_FRONT_DESK.contains(id)) {
                    events.add(SimpleConditionEvent.violated(method, id
                            + " changes state but has no @PreAuthorize and is not listed in OPEN_TO_THE_FRONT_DESK"));
                }
            }
        };
    }

    private static boolean changesState(final JavaMethod method) {
        return method.isAnnotatedWith(PostMapping.class)
                || method.isAnnotatedWith(PutMapping.class)
                || method.isAnnotatedWith(PatchMapping.class)
                || method.isAnnotatedWith(DeleteMapping.class);
    }

    private static boolean hasPreAuthorize(final JavaMethod method) {
        final JavaClass owner = method.getOwner();
        return method.isAnnotatedWith(PreAuthorize.class) || owner.isAnnotatedWith(PreAuthorize.class);
    }
}
