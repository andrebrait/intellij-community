// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.ui.LanguageTextField
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider
import org.jetbrains.plugins.terminal.exp.completion.TerminalShellSupport
import org.jetbrains.plugins.terminal.util.ShellType
import java.awt.Color
import java.awt.Dimension
import javax.swing.*

class TerminalPromptPanel(private val project: Project,
                          private val settings: JBTerminalSystemSettingsProviderBase,
                          session: TerminalSession,
                          private val commandExecutor: TerminalCommandExecutor) : JPanel(), ComponentContainer, ShellCommandListener {
  private val editorTextField: LanguageTextField
  private val editor: EditorImpl
    get() = editorTextField.getEditor(true) as EditorImpl
  private val document: Document
    get() = editorTextField.document

  private val promptLabel: JLabel

  private val completionManager: TerminalCompletionManager?

  private val commandHistoryManager: CommandHistoryManager
  private val commandHistoryPresenter: CommandHistoryPresenter

  val charSize: Dimension
    get() = Dimension(editor.charHeight, editor.lineHeight)

  init {
    editorTextField = createPromptTextField(session)

    promptLabel = createPromptLabel()
    promptLabel.text = computePromptText(TerminalProjectOptionsProvider.getInstance(project).startingDirectory ?: "")

    completionManager = when (session.shellIntegration?.shellType) {
      ShellType.ZSH -> ZshCompletionManager(session)
      ShellType.BASH -> BashCompletionManager(session)
      else -> null
    }

    commandHistoryManager = CommandHistoryManager(session)
    commandHistoryPresenter = CommandHistoryPresenter(project, editor, commandExecutor)

    editor.putUserData(TerminalSession.KEY, session)
    editor.putUserData(TerminalCompletionManager.KEY, completionManager)

    session.addCommandListener(this)

    val innerBorder = JBUI.Borders.customLine(UIUtil.getTextFieldBackground(), 6, 0, 6, 0)
    val outerBorder = JBUI.Borders.customLineTop(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
    border = JBUI.Borders.compound(outerBorder, innerBorder)

    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    add(promptLabel)
    add(Box.createRigidArea(JBDimension(0, 4)))
    add(editorTextField)
  }

  private fun createPromptTextField(session: TerminalSession): LanguageTextField {
    val shellType = session.shellIntegration?.shellType
    val language = if (shellType != null) {
      TerminalShellSupport.findByShellType(shellType)?.promptLanguage ?: PlainTextLanguage.INSTANCE
    }
    else PlainTextLanguage.INSTANCE
    val textField = object : LanguageTextField(language, project, "", false) {
      override fun setBackground(bg: Color?) {
        // do nothing to not set background to editor in super method
      }
    }
    textField.setDisposedWith(this)
    textField.border = JBUI.Borders.emptyLeft(JBUI.scale(LEFT_INSET))
    textField.alignmentX = JComponent.LEFT_ALIGNMENT

    val editor = textField.getEditor(true) as EditorImpl
    editor.scrollPane.border = JBUI.Borders.empty()
    editor.backgroundColor = UIUtil.getTextFieldBackground()
    editor.colorsScheme.apply {
      editorFontName = settings.terminalFont.fontName
      editorFontSize = settings.terminalFont.size
      lineSpacing = settings.lineSpacing
    }
    editor.settings.isBlockCursor = true
    editor.putUserData(KEY, this)  // to access this panel from editor action handlers

    return textField
  }

  private fun createPromptLabel(): JLabel {
    val label = JLabel()
    label.font = EditorUtil.getEditorFont()
    label.border = JBUI.Borders.emptyLeft(JBUI.scale(LEFT_INSET))
    label.alignmentX = JComponent.LEFT_ALIGNMENT
    return label
  }

  override fun directoryChanged(newDirectory: @NlsSafe String) {
    invokeLater {
      promptLabel.text = computePromptText(newDirectory)
    }
  }

  private fun computePromptText(directory: String): @NlsSafe String {
    return if (directory != SystemProperties.getUserHome()) {
      FileUtil.getLocationRelativeToUserHome(directory)
    }
    else "~"
  }

  @RequiresEdt
  fun reset() {
    runWriteAction {
      document.setText("")
    }
  }

  fun handleEnterPressed() {
    commandExecutor.startCommandExecution(document.text)
  }

  fun showCommandHistory() {
    val history = commandHistoryManager.history
    if (history.isNotEmpty()) {
      commandHistoryPresenter.showCommandHistory(history)
    }
  }

  fun onCommandHistoryClosed() {
    commandHistoryPresenter.onCommandHistoryClosed()
  }

  fun isFocused(): Boolean {
    return editor.contentComponent.hasFocus()
  }

  override fun getBackground(): Color {
    return UIUtil.getTextFieldBackground()
  }

  override fun getComponent(): JComponent = this

  override fun getPreferredFocusableComponent(): JComponent = editor.contentComponent

  override fun dispose() {

  }

  companion object {
    val KEY: Key<TerminalPromptPanel> = Key.create("TerminalPromptPanel")

    private const val LEFT_INSET: Int = 7
  }
}