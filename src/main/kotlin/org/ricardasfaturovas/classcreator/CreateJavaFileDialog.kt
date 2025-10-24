package org.ricardasfaturovas.classcreator

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.ui.TextFieldWithAutoCompletion
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import java.awt.Dimension
import javax.swing.JComponent

class CreateJavaFileDialog(
    private val project: Project,
    private val currentFile: VirtualFile?,
    private val fileType: JavaFileType
) : DialogWrapper(project) {

    private val nameField = JBTextField()
    private var packageNameField: JComponent = JBTextField()

    init {
        title = "Create Java ${fileType.displayName}"
        setupInitialPackageField()
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = panel {
            row("${fileType.displayName} name:") {
                cell(nameField)
                    .validationOnApply {
                        val name = nameField.text.trim()
                        if (name.isEmpty()) {
                            error("${fileType.displayName} name cannot be empty")
                        }
                        if (!isValidJavaIdentifier(name)) {
                            error("Invalid ${fileType.displayName.lowercase()} name")
                        }
                        else {
                            null
                        }
                    }
                    .resizableColumn()
                    .columns(27)
            }
            row("Package name:") {
                cell(packageNameField)
                    .validationOnApply {
                        val packageName = getPackageFieldText()
                        if (!isValidPackageName(packageName)) {
                            error("Invalid package name")
                        } else {
                            null
                        }
                    }
                    .resizableColumn()
            }
        }
        if (packageNameField is TextFieldWithAutoCompletion<*>) {
            (packageNameField as TextFieldWithAutoCompletion<*>).preferredSize =
               Dimension(305,30)
        }

        panel.preferredSize = Dimension(400, 100)
        return panel
    }

    private fun setupInitialPackageField() {
        val initialPackageName = getCurrentPackageName()
        val existingPackages = getExistingPackages()

        val completionProvider = TextFieldWithAutoCompletion.StringsCompletionProvider(
            existingPackages,
            null
        )

        packageNameField = TextFieldWithAutoCompletion(
            project,
            completionProvider,
            true,
            initialPackageName
        )
    }

    private fun getPackageFieldText(): String {
        return when (packageNameField) {
            is TextFieldWithAutoCompletion<*> -> (packageNameField as TextFieldWithAutoCompletion<*>).text.trim()
            is JBTextField -> (packageNameField as JBTextField).text.trim()
            else -> ""
        }
    }

    private fun isValidJavaIdentifier(name: String): Boolean {
        if (name.isEmpty() || !Character.isJavaIdentifierStart(name[0])) return false
        return name.all { Character.isJavaIdentifierPart(it) }
    }

    private fun isValidPackageName(packageName: String): Boolean {
        return packageName.split('.').all { part ->
            part.isNotEmpty() && isValidJavaIdentifier(part)
        }
    }

    private fun getCurrentPackageName(): String {
        if (currentFile == null) return ""

        val psiFile = PsiManager.getInstance(project).findFile(currentFile)
        if (psiFile is PsiJavaFile) {
            val javaPackageName = psiFile.packageName
            return getFullyQualifiedPackageName(currentFile, javaPackageName)
        }

        val directory = if (currentFile.isDirectory) currentFile else currentFile.parent
        directory?.let { dir ->
            val psiDirectory = PsiManager.getInstance(project).findDirectory(dir)
            psiDirectory?.let { psiDir ->
                val javaPackageName = JavaDirectoryService.getInstance().getPackage(psiDir)?.qualifiedName ?: ""
                return getFullyQualifiedPackageName(dir, javaPackageName)
            }
        }

        return ""
    }

    private fun getFullyQualifiedPackageName(file: VirtualFile, javaPackageName: String): String {
        val projectDir = project.guessProjectDir() ?: return javaPackageName

        val sourceRoots = ProjectRootManager.getInstance(project).contentSourceRoots

        for (sourceRoot in sourceRoots) {
            if (VfsUtil.isAncestor(sourceRoot, file, false)) {
                val relativePath = VfsUtil.getRelativePath(sourceRoot, projectDir)
                val sourceRootPath = relativePath?.replace('/', '.') ?: ""

                return if (sourceRootPath.isNotEmpty() && javaPackageName.isNotEmpty()) {
                    "$sourceRootPath.$javaPackageName"
                } else if (sourceRootPath.isNotEmpty()) {
                    sourceRootPath
                } else {
                    javaPackageName
                }
            }
        }

        return javaPackageName
    }

    private fun getExistingPackages(): List<String> {
        val packages = mutableSetOf<String>()

        ProjectRootManager.getInstance(project).contentSourceRoots.forEach { sourceRoot ->
            collectPackagesFromDirectory(sourceRoot, packages)
        }

        return packages.sorted()
    }

    private fun collectPackagesFromDirectory(directory: VirtualFile, packages: MutableSet<String>) {
        VfsUtil.visitChildrenRecursively(directory, object : VirtualFileVisitor<Void>() {
            override fun visitFile(file: VirtualFile): Boolean {
                if (!file.isDirectory) return true

                val psiDirectory = PsiManager.getInstance(project).findDirectory(file) ?: return true
                val javaPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory)

                if (javaPackage != null && javaPackage.qualifiedName.isNotEmpty()) {
                    val projectDir = project.guessProjectDir()
                    val sourceRootPath = if (projectDir != null) {
                        getSourceRootPath(file, projectDir)
                    } else ""

                    val fullPackageName = if (sourceRootPath.isNotEmpty()) {
                        "$sourceRootPath.${javaPackage.qualifiedName}"
                    } else {
                        javaPackage.qualifiedName
                    }
                    packages.add(fullPackageName)
                }

                return true
            }
        })
    }

    private fun getSourceRootPath(currentDir: VirtualFile, projectDir: VirtualFile): String {
        val sourceRoots = ProjectRootManager.getInstance(project).contentSourceRoots

        for (sourceRoot in sourceRoots) {
            if (VfsUtil.isAncestor(sourceRoot, currentDir, false)) {
                val relativePath = VfsUtil.getRelativePath(sourceRoot, projectDir)
                return relativePath?.replace('/', '.') ?: ""
            }
        }

        return ""
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return nameField
    }
    fun getName(): String = nameField.text.trim()
    fun getPackageName(): String = getPackageFieldText()
}