package org.jetbrains.research.intellijdeodorant.ide.ui;

import com.intellij.analysis.AnalysisScope;
import com.intellij.ide.util.EditorHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.research.intellijdeodorant.IntelliJDeodorantBundle;
import org.jetbrains.research.intellijdeodorant.JDeodorantFacade;
import org.jetbrains.research.intellijdeodorant.core.distance.MoveMethodCandidateRefactoring;
import org.jetbrains.research.intellijdeodorant.core.distance.ProjectInfo;
import org.jetbrains.research.intellijdeodorant.ide.refactoring.moveMethod.MoveMethodRefactoring;
import org.jetbrains.research.intellijdeodorant.ide.refactoring.RefactoringsApplier;
import org.jetbrains.research.intellijdeodorant.ide.ui.listeners.DoubleClickListener;
import org.jetbrains.research.intellijdeodorant.utils.ExportResultsUtil;

import javax.swing.*;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

import static javax.swing.ListSelectionModel.SINGLE_SELECTION;
import static org.jetbrains.research.intellijdeodorant.ide.ui.MoveMethodTableModel.SELECTION_COLUMN_INDEX;

/**
 * Panel for Move Method refactoring.
 */
class MoveMethodPanel extends JPanel {
    private static final String SELECT_ALL_BUTTON_TEXT_KEY = "select.all.button";
    private static final String DESELECT_ALL_BUTTON_TEXT_KEY = "deselect.all.button";
    private static final String REFACTOR_BUTTON_TEXT_KEY = "refactor.button";
    private static final String REFRESH_BUTTON_TEXT_KEY = "refresh.button";
    private static final String DETECT_INDICATOR_STATUS_TEXT_KEY = "feature.envy.detect.indicator.status";
    private static final String TOTAL_LABEL_TEXT_KEY = "total.label";
    private static final String EXPORT_BUTTON_TEXT_KEY = "export";
    private static final String REFRESH_NEEDED_TEXT = "press.refresh.to.find.refactoring.opportunities";

    @NotNull
    private final AnalysisScope scope;
    @NotNull
    private final MoveMethodTableModel model;
    private final JBTable table = new JBTable();
    private final JButton selectAllButton = new JButton();
    private final JButton deselectAllButton = new JButton();
    private final JButton doRefactorButton = new JButton();
    private final JLabel infoLabel = new JLabel();
    private final JLabel info = new JLabel();
    private final JButton refreshButton = new JButton();
    private final List<MoveMethodRefactoring> refactorings = new ArrayList<>();
    private JScrollPane scrollPane = new JBScrollPane();
    private final JButton exportButton = new JButton();
    private JLabel refreshLabel = new JLabel(
            IntelliJDeodorantBundle.message(REFRESH_NEEDED_TEXT),
            SwingConstants.CENTER
    );

    MoveMethodPanel(@NotNull AnalysisScope scope) {
        this.scope = scope;
        setLayout(new BorderLayout());
        model = new MoveMethodTableModel(refactorings);
        setupGUI();
    }

    private void setupGUI() {
        add(createTablePanel(), BorderLayout.CENTER);
        add(createButtonsPanel(), BorderLayout.SOUTH);
    }

    private JScrollPane createTablePanel() {
        new TableSpeedSearch(table);
        table.setModel(model);
        model.setupRenderer(table);
        table.addMouseListener((DoubleClickListener) this::onDoubleClick);
        table.getSelectionModel().setSelectionMode(SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        setupTableLayout();
        refreshLabel.setForeground(JBColor.GRAY);
        scrollPane = ScrollPaneFactory.createScrollPane(table);
        scrollPane.setViewportView(refreshLabel);
        return scrollPane;
    }

    private void setupTableLayout() {
        final TableColumn selectionColumn = table.getTableHeader().getColumnModel().getColumn(SELECTION_COLUMN_INDEX);
        selectionColumn.setMaxWidth(30);
        selectionColumn.setMinWidth(30);

        final TableColumn dependencies = table.getTableHeader().getColumnModel().getColumn(SELECTION_COLUMN_INDEX);
        dependencies.setMaxWidth(30);
        dependencies.setMinWidth(30);
    }

    private JComponent createButtonsPanel() {
        final JPanel panel = new JPanel(new BorderLayout());
        final JPanel buttonsPanel = new JBPanel<JBPanel<JBPanel>>();
        buttonsPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));

        infoLabel.setText(IntelliJDeodorantBundle.message(TOTAL_LABEL_TEXT_KEY) + model.getRowCount());
        infoLabel.setPreferredSize(new Dimension(80, 30));
        buttonsPanel.add(infoLabel);

        selectAllButton.setText(IntelliJDeodorantBundle.message(SELECT_ALL_BUTTON_TEXT_KEY));
        selectAllButton.addActionListener(e -> model.selectAll());
        selectAllButton.setEnabled(false);
        buttonsPanel.add(selectAllButton);

        deselectAllButton.setText(IntelliJDeodorantBundle.message(DESELECT_ALL_BUTTON_TEXT_KEY));
        deselectAllButton.addActionListener(e -> model.deselectAll());
        deselectAllButton.setEnabled(false);
        buttonsPanel.add(deselectAllButton);

        doRefactorButton.setText(IntelliJDeodorantBundle.message(REFACTOR_BUTTON_TEXT_KEY));
        doRefactorButton.addActionListener(e -> refactorSelected());
        doRefactorButton.setEnabled(false);
        buttonsPanel.add(doRefactorButton);

        refreshButton.setText(IntelliJDeodorantBundle.message(REFRESH_BUTTON_TEXT_KEY));
        refreshButton.addActionListener(l -> refreshPanel());
        buttonsPanel.add(refreshButton);

        exportButton.setText(IntelliJDeodorantBundle.message(EXPORT_BUTTON_TEXT_KEY));
        exportButton.addActionListener(e -> ExportResultsUtil.export(getValidRefactoringsSuggestions(), this));
        exportButton.setEnabled(false);
        buttonsPanel.add(exportButton);
        panel.add(buttonsPanel, BorderLayout.EAST);

        model.addTableModelListener(l -> enableButtonsOnConditions());

        panel.add(info, BorderLayout.WEST);
        return panel;
    }

    private List<MoveMethodRefactoring> getValidRefactoringsSuggestions() {
        return refactorings.stream()
                .filter(refactoring -> refactoring.getOptionalMethod()
                        .isPresent())
                .collect(Collectors.toList());
    }

    private void enableButtonsOnConditions() {
        doRefactorButton.setEnabled(model.isAnySelected());
        selectAllButton.setEnabled(model.getRowCount() != 0);
        deselectAllButton.setEnabled(model.isAnySelected());
        refreshButton.setEnabled(true);
        exportButton.setEnabled(refactorings.stream()
                .anyMatch(refactoring -> refactoring.getOptionalMethod().isPresent()));
    }

    private void disableAllButtons() {
        doRefactorButton.setEnabled(false);
        selectAllButton.setEnabled(false);
        deselectAllButton.setEnabled(false);
        refreshButton.setEnabled(false);
        exportButton.setEnabled(false);
    }

    private void refactorSelected() {
        disableAllButtons();
        table.setEnabled(false);

        final Set<MoveMethodRefactoring> selectedRefactorings = new HashSet<>(model.pullSelected());

        Set<MoveMethodRefactoring> appliedRefactorings = RefactoringsApplier.moveRefactoring(new ArrayList<>(selectedRefactorings), scope);
        model.setAppliedRefactorings(new HashSet<>(appliedRefactorings));
        table.setEnabled(true);

        enableButtonsOnConditions();
    }

    private void refreshPanel() {
        disableAllButtons();
        refactorings.clear();
        model.clearTable();
        infoLabel.setText(IntelliJDeodorantBundle.message(TOTAL_LABEL_TEXT_KEY) + model.getRowCount());
        scrollPane.setVisible(false);
        calculateRefactorings();
    }

    private void calculateRefactorings() {
        Project project = scope.getProject();
        ProjectInfo projectInfo = new ProjectInfo(project);

        final Task.Backgroundable backgroundable = new Task.Backgroundable(project,
                IntelliJDeodorantBundle.message(DETECT_INDICATOR_STATUS_TEXT_KEY), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                ApplicationManager.getApplication().runReadAction(() -> {
                    List<MoveMethodCandidateRefactoring> candidates = JDeodorantFacade.getMoveMethodRefactoringOpportunities(projectInfo, indicator);
                    final List<MoveMethodRefactoring> references = candidates.stream().filter(Objects::nonNull)
                            .map(x ->
                                    new MoveMethodRefactoring(x.getSourceMethodDeclaration(),
                                            x.getTargetClass().getClassObject().getPsiClass(),
                                            x.getDistinctSourceDependencies(),
                                            x.getDistinctTargetDependencies()))
                            .collect(Collectors.toList());
                    refactorings.clear();
                    refactorings.addAll(new ArrayList<>(references));
                    model.updateTable(refactorings);
                    infoLabel.setText(IntelliJDeodorantBundle.message(TOTAL_LABEL_TEXT_KEY) + model.getRowCount());
                    scrollPane.setVisible(true);
                    scrollPane.setViewportView(table);
                    enableButtonsOnConditions();
                });
            }
        };
        ProgressManager.getInstance().run(backgroundable);
    }

    private void onDoubleClick() {
        final int selectedRow = table.getSelectedRow() == -1 ? -1 : table.convertRowIndexToModel(table.getSelectedRow());
        final int selectedColumn = table.getSelectedColumn();
        if (selectedRow == -1 || selectedColumn == -1 || selectedColumn == SELECTION_COLUMN_INDEX) {
            return;
        }
        openDefinition(model.getUnitAt(selectedRow, selectedColumn).orElse(null), scope);
    }

    private static void openDefinition(@Nullable PsiMember unit, AnalysisScope scope) {
        new Task.Backgroundable(scope.getProject(), "Search Definition") {
            private PsiElement result;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                result = unit;
            }

            @Override
            public void onSuccess() {
                if (result != null) {
                    EditorHelper.openInEditor(result);
                }
            }
        }.queue();
    }
}