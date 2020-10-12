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

package com.liferay.ide.idea.ui.modules;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import com.liferay.ide.idea.core.WorkspaceConstants;
import com.liferay.ide.idea.util.BladeCLI;
import com.liferay.ide.idea.util.FileUtil;
import com.liferay.ide.idea.util.ListUtil;

import java.awt.GridLayout;

import java.io.File;
import java.io.FileNotFoundException;

import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * @author Ethan Sun
 */
public class LiferayWorkspaceProductDialog extends DialogWrapper {

	@Override
	public void doCancelAction() {
		super.doCancelAction();
	}

	protected LiferayWorkspaceProductDialog(@NotNull Project project) {
		super(true);

		_project = project;

		init();

		setTitle("Configure Product For Liferay Workspace");
	}

	@Nullable
	@Override
	protected JComponent createCenterPanel() {
		JPanel dialogPanel = new JPanel(new GridLayout(2, 2));

		JLabel productVersionLabel = new JLabel("Product Version:");

		_productVersionComboBox = new ComboBox<>();

		JLabel showAllProductVersionLabel = new JLabel("Show All Product Version:");

		JCheckBox showAllProductVersionCheckBox = new JCheckBox();

		showAllProductVersionCheckBox.setSelected(false);

		showAllProductVersionCheckBox.addActionListener(
			e -> {
				boolean showAll = showAllProductVersionCheckBox.isSelected();

				modifyProductVersion(showAll);
			});

		dialogPanel.add(productVersionLabel);

		dialogPanel.add(_productVersionComboBox);

		dialogPanel.add(showAllProductVersionLabel);

		dialogPanel.add(showAllProductVersionCheckBox);

		modifyProductVersion(false);

		return dialogPanel;
	}

	@Override
	protected void doOKAction() {
		_application.runWriteAction(
			() -> {
				try {
					final String productKey = (String)_productVersionComboBox.getSelectedItem();

					Path projectPath = Paths.get(Objects.requireNonNull(_project.getBasePath()));

					Path gradlePropertiesPath = projectPath.resolve("gradle.properties");

					File propertyFile = gradlePropertiesPath.toFile();

					if (FileUtil.notExists(propertyFile)) {
						throw new FileNotFoundException();
					}

					PropertiesConfiguration config = new PropertiesConfiguration(propertyFile);

					config.setProperty(WorkspaceConstants.WORKSPACE_PRODUCT_PROPERTY, productKey);

					config.save();

					VirtualFile gradlePropeerties = VfsUtil.findFile(gradlePropertiesPath, false);

					VfsUtil.markDirtyAndRefresh(true, true, true, gradlePropeerties);
				}
				catch (ConfigurationException | FileNotFoundException e) {
					String exceptionMessage = "<b>Faild to save liferay.workspace.product in gradle.properties.</b>";

					NotificationData notificationData = new NotificationData(
						exceptionMessage, "<i>" + _project.getName() + "</i> \n" + e.getMessage(),
						NotificationCategory.WARNING, NotificationSource.TASK_EXECUTION);

					notificationData.setBalloonNotification(true);

					ExternalSystemNotificationManager externalSystemNotificationManager =
						ExternalSystemNotificationManager.getInstance(_project);

					externalSystemNotificationManager.showNotification(GradleConstants.SYSTEM_ID, notificationData);
				}
			});

		super.doOKAction();
	}

	@Nullable
	@Override
	protected ValidationInfo doValidate() {
		if (_productVersionComboBox.getSelectedIndex() == -1) {
			return new ValidationInfo("Please configure product version");
		}

		return super.doValidate();
	}

	protected void modifyProductVersion(boolean showAll) {
		_application.executeOnPooledThread(
			() -> {
				String[] allWorkspaceProducts = BladeCLI.getWorkspaceProducts(showAll);

				if (!ListUtil.isEmpty(allWorkspaceProducts)) {
					_productVersions.clear();

					_productVersionComboBox.removeAllItems();

					Collections.addAll(_productVersions, allWorkspaceProducts);
				}

				for (String productVersion : _productVersions) {
					_productVersionComboBox.addItem(productVersion);
				}

				_productVersionComboBox.setSelectedIndex(0);
			});
	}

	private final Application _application = ApplicationManager.getApplication();
	private JComboBox<String> _productVersionComboBox;
	private final List<String> _productVersions = new CopyOnWriteArrayList<>();

	@NotNull
	private final Project _project;

}