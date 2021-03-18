/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.ide.idea.ui.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.VirtualFile;

import com.liferay.ide.idea.core.LiferayIcons;
import com.liferay.ide.idea.util.LiferayWorkspaceSupport;

/**
 * @author Seiphon Wang
 */
public class BuildDockerImageAction extends AbstractLiferayGradleTaskAction implements LiferayWorkspaceSupport {

	public BuildDockerImageAction() {
		super("BuildDockerImage", "Run buildDockerImage task", LiferayIcons.LIFERAY_ICON, "buildDockerImage");
	}

	@Override
	protected boolean isEnabledAndVisible(AnActionEvent anActionEvent) {
		if (super.isEnabledAndVisible(anActionEvent)) {
			VirtualFile baseDir = LiferayWorkspaceSupport.getWorkspaceVirtualFile(anActionEvent.getProject());

			if (baseDir == null) {
				return false;
			}

			if (baseDir.equals(getVirtualFile(anActionEvent))) {
				return true;
			}
		}

		return false;
	}

}