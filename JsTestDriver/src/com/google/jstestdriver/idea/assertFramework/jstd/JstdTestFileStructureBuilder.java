package com.google.jstestdriver.idea.assertFramework.jstd;

import com.google.jstestdriver.idea.assertFramework.AbstractTestFileStructureBuilder;
import com.google.jstestdriver.idea.util.JsPsiUtils;
import com.intellij.lang.javascript.JSTokenTypes;
import com.intellij.lang.javascript.psi.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JstdTestFileStructureBuilder extends AbstractTestFileStructureBuilder {

  private static final JstdTestFileStructureBuilder INSTANCE = new JstdTestFileStructureBuilder();

  private JstdTestFileStructureBuilder() {}

  @NotNull
  @Override
  public JstdTestFileStructure buildTestFileStructure(@NotNull JSFile jsFile) {
    JstdTestFileStructure jsTestFileStructure = new JstdTestFileStructure(jsFile);
    List<JSStatement> statements = JsPsiUtils.listStatementsInExecutionOrder(jsFile);
    for (JSStatement statement : statements) {
      fillJsTestFileStructure(jsTestFileStructure, statement);
    }
    for (JstdTestCaseStructure testCaseStructure : jsTestFileStructure.getTestCaseStructures()) {
      for (JstdTestStructure testStructure : testCaseStructure.getTestStructures()) {
        PsiElement anchor = testStructure.getTestMethodNameDeclaration();
        anchor.putUserData(JstdTestFileStructure.TEST_ELEMENT_NAME_KEY, testStructure.getName());
      }
      JSExpression testCaseMethodExpr = testCaseStructure.getEnclosingCallExpression().getMethodExpression();
      if (testCaseMethodExpr != null) {
        testCaseMethodExpr.putUserData(JstdTestFileStructure.TEST_ELEMENT_NAME_KEY, testCaseStructure.getName());
      }
    }
    return jsTestFileStructure;
  }

  private static void fillJsTestFileStructure(JstdTestFileStructure jsTestFileStructure, JSStatement jsElement) {
    {
      JSExpressionStatement jsExpressionStatement = ObjectUtils.tryCast(jsElement, JSExpressionStatement.class);
      if (jsExpressionStatement != null) {
        {
          JSCallExpression callExpression = ObjectUtils.tryCast(jsExpressionStatement.getExpression(), JSCallExpression.class);
          if (callExpression != null) {
            // TestCase("testCaseName", { test1: function() {} });
            createTestCaseStructure(jsTestFileStructure, callExpression);
          }
        }
        {
          // testCase = TestCase("testCaseName");
          JSAssignmentExpression jsAssignmentExpression = ObjectUtils.tryCast(jsExpressionStatement.getExpression(), JSAssignmentExpression.class);
          if (jsAssignmentExpression != null) {
            JSCallExpression jsCallExpression = ObjectUtils.tryCast(jsAssignmentExpression.getROperand(), JSCallExpression.class);
            if (jsCallExpression != null) {
              JstdTestCaseStructure testCaseStructure = createTestCaseStructure(jsTestFileStructure, jsCallExpression);
              if (testCaseStructure != null) {
                JSDefinitionExpression jsDefinitionExpression = ObjectUtils.tryCast(jsAssignmentExpression.getLOperand(), JSDefinitionExpression.class);
                if (jsDefinitionExpression != null) {
                  JSReferenceExpression jsReferenceExpression = ObjectUtils.tryCast(jsDefinitionExpression.getExpression(), JSReferenceExpression.class);
                  if (jsReferenceExpression != null) {
                    String refName = jsReferenceExpression.getReferencedName();
                    if (refName != null) {
                      addPrototypeTests(testCaseStructure, refName, jsExpressionStatement);
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    {
      // var testCase = TestCase("testCaseName");
      JSVarStatement jsVarStatement = ObjectUtils.tryCast(jsElement, JSVarStatement.class);
      if (jsVarStatement != null) {
        JSVariable[] jsVariables = ObjectUtils.notNull(jsVarStatement.getVariables(), JSVariable.EMPTY_ARRAY);
        for (JSVariable jsVariable : jsVariables) {
          JSCallExpression jsCallExpression = ObjectUtils.tryCast(jsVariable.getInitializer(), JSCallExpression.class);
          if (jsCallExpression != null) {
            JstdTestCaseStructure testCaseStructure = createTestCaseStructure(jsTestFileStructure, jsCallExpression);
            if (testCaseStructure != null) {
              String refName = jsVariable.getQualifiedName();
              if (refName != null) {
                addPrototypeTests(testCaseStructure, refName, jsVarStatement);
              }
            }
          }
        }
      }
    }
  }

  private static void addPrototypeTests(@NotNull JstdTestCaseStructure testCaseStructure,
                                        @NotNull String referenceName,
                                        @NotNull JSStatement refStatement) {
    List<JSStatement> statements = JsPsiUtils.listStatementsInExecutionOrderNextTo(refStatement);
    for (JSStatement statement : statements) {
      JSExpressionStatement expressionStatement = ObjectUtils.tryCast(statement, JSExpressionStatement.class);
      if (expressionStatement != null) {
        JSAssignmentExpression assignmentExpr = ObjectUtils.tryCast(expressionStatement.getExpression(), JSAssignmentExpression.class);
        if (assignmentExpr != null) {
          JSDefinitionExpression wholeLeftDefExpr = ObjectUtils.tryCast(assignmentExpr.getLOperand(), JSDefinitionExpression.class);
          if (wholeLeftDefExpr != null) {
            JSReferenceExpression wholeLeftRefExpr = ObjectUtils.tryCast(wholeLeftDefExpr.getExpression(), JSReferenceExpression.class);
            if (wholeLeftRefExpr != null) {
              JSReferenceExpression testCaseAndPrototypeRefExpr = ObjectUtils.tryCast(wholeLeftRefExpr.getQualifier(), JSReferenceExpression.class);
              if (testCaseAndPrototypeRefExpr != null) {
                if ("prototype".equals(testCaseAndPrototypeRefExpr.getReferencedName())) {
                  JSReferenceExpression testCaseRefExpr = ObjectUtils.tryCast(testCaseAndPrototypeRefExpr.getQualifier(), JSReferenceExpression.class);
                  if (testCaseRefExpr != null && testCaseRefExpr.getQualifier() == null) {
                    if (referenceName.equals(testCaseRefExpr.getReferencedName())) {
                      addPrototypeTest(testCaseStructure, assignmentExpr.getROperand(), wholeLeftRefExpr.getReferenceNameElement());
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  private static void addPrototypeTest(@NotNull JstdTestCaseStructure testCaseStructure,
                                       @Nullable JSExpression rightAssignmentOperand,
                                       @Nullable PsiElement testMethodIdentifierPsiElement) {
    LeafPsiElement leafPsiElement = ObjectUtils.tryCast(testMethodIdentifierPsiElement, LeafPsiElement.class);
    if (leafPsiElement != null && leafPsiElement.getElementType() == JSTokenTypes.IDENTIFIER) {
      JSFunctionExpression jsFunctionExpression = JsPsiUtils.extractFunctionExpression(rightAssignmentOperand);
      JstdTestStructure jstdTestStructure = JstdTestStructure.newPrototypeBasedTestStructure(leafPsiElement, jsFunctionExpression);
      testCaseStructure.addTestStructure(jstdTestStructure);
    }
  }

  @Nullable
  private static JstdTestCaseStructure createTestCaseStructure(@NotNull JstdTestFileStructure jsTestFileStructure,
                                                               @NotNull JSCallExpression testCaseCallExpression) {
    JSReferenceExpression referenceExpression = ObjectUtils.tryCast(testCaseCallExpression.getMethodExpression(), JSReferenceExpression.class);
    if (referenceExpression != null) {
      String referenceName = referenceExpression.getReferencedName();
      if ("TestCase".equals(referenceName) || "AsyncTestCase".equals(referenceName)) {
        JSExpression[] arguments = JsPsiUtils.getArguments(testCaseCallExpression);
        if (arguments.length >= 1) {
          String testCaseName = JsPsiUtils.extractStringValue(arguments[0]);
          if (testCaseName != null) {
            JSObjectLiteralExpression testsObjectLiteral = null;
            if (arguments.length >= 2) {
              testsObjectLiteral = JsPsiUtils.extractObjectLiteralExpression(arguments[1]);
            }
            JstdTestCaseStructure testCaseStructure = new JstdTestCaseStructure(jsTestFileStructure, testCaseName, testCaseCallExpression, testsObjectLiteral);
            jsTestFileStructure.addTestCaseStructure(testCaseStructure);
            if (testsObjectLiteral != null) {
              fillTestCaseStructureByObjectLiteral(testCaseStructure, testsObjectLiteral);
            }
            return testCaseStructure;
          }
        }
      }
    }
    return null;
  }

  private static void fillTestCaseStructureByObjectLiteral(
      @NotNull JstdTestCaseStructure testCaseStructure,
      @NotNull JSObjectLiteralExpression testsObjectLiteral
  ) {
    JSProperty[] properties = JsPsiUtils.getProperties(testsObjectLiteral);
    for (JSProperty property : properties) {
      JstdTestStructure testStructure = JstdTestStructure.newPropertyBasedTestStructure(property);
      if (testStructure != null) {
        testCaseStructure.addTestStructure(testStructure);
      }
    }
  }

  public static JstdTestFileStructureBuilder getInstance() {
    return INSTANCE;
  }
}
