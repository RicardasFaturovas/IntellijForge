package org.ricardasfaturovas.classcreator

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*

class CreateClassPopupAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)

        val dialog = CreateClassDialog(project, virtualFile)
        if (dialog.showAndGet()) {
            val className = dialog.getClassName()
            val packageName = dialog.getPackageName()
            createJavaClass(project, className, packageName)
        }
    }

    private fun createJavaClass(project: Project, className: String, packageName: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            val psiManager = PsiManager.getInstance(project)

            val (sourceRoot, javaPackageName) = parseFullPackageName(packageName, project)

            val packageDir = findOrCreatePackageDirectory(sourceRoot, javaPackageName)

            val psiDirectory = psiManager.findDirectory(packageDir) ?: return@runWriteCommandAction

            val file = JavaDirectoryService.getInstance().createClass(psiDirectory, className)
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            file.containingFile.navigate(true)
        }
    }
    private fun parseFullPackageName(fullPackageName: String, project: Project): Pair<VirtualFile, String> {
        if (fullPackageName.isEmpty()) {
            return Pair(findSourceRoot(project)!!, "")
        }

        val projectDir = project.guessProjectDir()!!

        val sourceRoots = ProjectRootManager.getInstance(project).contentSourceRoots

        for (sourceRoot in sourceRoots) {
            val relativePath = VfsUtil.getRelativePath(sourceRoot, projectDir)?.replace('/', '.')
            if (relativePath != null && fullPackageName.startsWith("$relativePath.")) {
                val javaPackage = fullPackageName.substring(relativePath.length + 1)
                return Pair(sourceRoot, javaPackage)
            }
        }

        return Pair(findSourceRoot(project)!!, fullPackageName)
    }

    private fun findSourceRoot(project: Project): VirtualFile? {
        val projectBaseDir = project.guessProjectDir() ?: return null
        return projectBaseDir.findChild("src")?.findChild("main")?.findChild("java")
            ?: projectBaseDir
    }

    private fun findOrCreatePackageDirectory(sourceRoot: VirtualFile, packageName: String): VirtualFile {
        if (packageName.isEmpty()) return sourceRoot

        val packageParts = packageName.split(".")
        var currentDir = sourceRoot

        for (part in packageParts) {
            val existingChild = currentDir.findChild(part)
            currentDir = if (existingChild != null && existingChild.isDirectory) {
                existingChild
            } else {
                currentDir.createChildDirectory(this, part)
            }
        }

        return currentDir
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}