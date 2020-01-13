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

package com.liferay.ide.idea.project;

import com.intellij.ProjectTopics;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetType;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.javaee.web.facet.WebFacetConfiguration;
import com.intellij.javaee.web.facet.WebFacetType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;

import com.liferay.ide.idea.util.FileUtil;
import com.liferay.ide.idea.util.LiferayWorkspaceSupport;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;

import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * @author Dominik Marks
 * @author Joye Luo
 * @author Charles Wu
 * @author Ethan Sun
 */
public class LiferayProjectComponent implements ProjectComponent {

	@Override
	public void disposeComponent() {
		_messageBusConnection.disconnect();
	}

	@Override
	public void initComponent() {
		MessageBus messageBus = _project.getMessageBus();

		_messageBusConnection = messageBus.connect();

		ModuleListener moduleListener = new ModuleListener() {

			@Override
			public void moduleAdded(@NotNull Project project, @NotNull Module module) {
				if (LiferayWorkspaceSupport.isValidWorkspaceLocation(project)) {
					_addWebRoot(module);
				}
			}

			@Override
			public void moduleRemoved(@NotNull Project project, @NotNull Module module) {
				if (LiferayWorkspaceSupport.isValidWorkspaceLocation(project)) {
					try {
						_removeModuleFromPom(module);
					}
					catch (IOException ioe) {
						NotificationData notificationData = new NotificationData(
							"<b>File Not Found</b>", "<i>pom.xml is inexistence</i> \n" + ioe.getMessage(),
							NotificationCategory.ERROR, NotificationSource.TASK_EXECUTION);

						notificationData.setBalloonNotification(true);

						ExternalSystemNotificationManager externalSystemNotificationManager =
							ExternalSystemNotificationManager.getInstance(module.getProject());

						externalSystemNotificationManager.showNotification(GradleConstants.SYSTEM_ID, notificationData);
					}
				}
			}

		};

		_messageBusConnection.subscribe(ProjectTopics.MODULES, moduleListener);
	}

	protected LiferayProjectComponent(Project project) {
		_project = project;
	}

	private void _addWebRoot(Module module) {
		ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);

		VirtualFile[] sourceRoots = moduleRootManager.getSourceRoots();

		if (sourceRoots.length > 0) {
			for (VirtualFile sourceRoot : sourceRoots) {
				String sourcePath = sourceRoot.getPath();

				if (sourcePath.contains("src/main/resources")) {
					String resourcesPath = sourcePath.concat("/META-INF/resources");

					LocalFileSystem localFileSystem = LocalFileSystem.getInstance();

					VirtualFile resources = localFileSystem.findFileByPath(resourcesPath);

					if (FileUtil.exist(resources)) {
						boolean hasWebFacet = false;

						FacetManager facetManager = FacetManager.getInstance(module);

						Facet<?>[] facets = facetManager.getAllFacets();

						for (Facet<?> facet : facets) {
							WebFacetType webFacetType = WebFacetType.getInstance();

							FacetType<?, ?> facetType = facet.getType();

							String facetTypePresentableName = facetType.getPresentableName();

							if (facetTypePresentableName.equals(webFacetType.getPresentableName())) {
								hasWebFacet = true;

								break;
							}
						}

						if (!hasWebFacet) {
							ProjectFacetManager projectFacetManager = ProjectFacetManager.getInstance(
								module.getProject());

							WebFacetConfiguration webFacetConfiguration =
								projectFacetManager.createDefaultConfiguration(WebFacetType.getInstance());

							ModifiableFacetModel modifiableFacetModel = facetManager.createModifiableModel();

							WebFacetType webFacetType = WebFacetType.getInstance();

							WebFacet webFacet = facetManager.createFacet(
								webFacetType, webFacetType.getPresentableName(), webFacetConfiguration, null);

							webFacet.addWebRoot(resources, "/");

							modifiableFacetModel.addFacet(webFacet);

							Application application = ApplicationManager.getApplication();

							application.invokeLater(() -> application.runWriteAction(modifiableFacetModel::commit));
						}
					}
				}
			}
		}
	}

	private void _removeModuleFromPom(Module module) throws IOException {
		String moduleFilePath = module.getModuleFilePath();

		int pathIndex = moduleFilePath.lastIndexOf("/");

		pathIndex = moduleFilePath.lastIndexOf("/", pathIndex - 1);

		String pomPath = moduleFilePath.substring(0, pathIndex + 1) + "pom.xml";

		File file = new File(pomPath);

		Model model = null;

		MavenXpp3Reader reader = new MavenXpp3Reader();

		try (InputStream fileInputStream = new FileInputStream(file)) {
			model = reader.read(fileInputStream, true);

			model.removeModule(module.getName());
		}
		catch (XmlPullParserException xppe) {
			NotificationData notificationData = new NotificationData(
				"<b>Parse Error</b>", "<i>Read pom.xml Failed</i> \n" + xppe.getMessage(), NotificationCategory.ERROR,
				NotificationSource.TASK_EXECUTION);

			notificationData.setBalloonNotification(true);

			ExternalSystemNotificationManager externalSystemNotificationManager =
				ExternalSystemNotificationManager.getInstance(module.getProject());

			externalSystemNotificationManager.showNotification(GradleConstants.SYSTEM_ID, notificationData);
		}

		MavenXpp3Writer writer = new MavenXpp3Writer();

		try (OutputStream outputStream = new FileOutputStream(file)) {
			assert model != null;

			writer.write(outputStream, model);
		}
	}

	private MessageBusConnection _messageBusConnection;
	private final Project _project;

}