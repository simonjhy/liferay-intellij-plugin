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

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.externalSystem.task.TaskCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.execution.ParametersListUtil;

import com.liferay.ide.idea.util.CoreUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Icon;

import org.gradle.cli.CommandLineArgumentException;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.execution.cmd.GradleCommandLineOptionsConverter;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * @author Andy Wu
 * @author Simon Jiang
 */
public abstract class AbstractLiferayGradleTaskAction extends AbstractLiferayAction {

	public AbstractLiferayGradleTaskAction(
		@Nullable String text, @Nullable String description, @Nullable Icon icon, String taskName) {

		super(text, description, icon);

		_taskName = taskName;
	}

	public void afterTask() {
	}

	public boolean continuous() {
		return false;
	}

	protected boolean checkProcess(
		ExternalTaskExecutionInfo taskExecutionInfo, @NotNull final String executorIdLocal,
		@NotNull final ExecutionEnvironment environmentLocal) {

		RunnerAndConfigurationSettings runAndConfigurationSettings =
			environmentLocal.getRunnerAndConfigurationSettings();

		RunConfiguration runConfiguration = runAndConfigurationSettings.getConfiguration();

		if (runConfiguration instanceof ExternalSystemRunConfiguration) {
			ExternalSystemRunConfiguration runnerExternalSystemRunConfiguration =
				(ExternalSystemRunConfiguration)runAndConfigurationSettings.getConfiguration();

			ExternalSystemTaskExecutionSettings runningTaskSettings =
				runnerExternalSystemRunConfiguration.getSettings();

			ExternalSystemTaskExecutionSettings taskRunnerSettings = taskExecutionInfo.getSettings();

			String externalPath = taskRunnerSettings.getExternalProjectPath();
			List<String> taskNames = taskRunnerSettings.getTaskNames();
			ProjectSystemId externalSystemId = taskRunnerSettings.getExternalSystemId();

			String executorId = taskExecutionInfo.getExecutorId();

			if (externalPath.equals(runningTaskSettings.getExternalProjectPath()) &&
				taskNames.equals(runningTaskSettings.getTaskNames()) &&
				externalSystemId.equals(runningTaskSettings.getExternalSystemId()) &&
				executorId.equals(executorIdLocal)) {

				return true;
			}
		}

		return false;
	}

	protected RunnerAndConfigurationSettings doExecute(final AnActionEvent event) {
		projectDir = getWorkingDirectory(event);

		final String workingDirectory = projectDir.getCanonicalPath();

		if (CoreUtil.isNullOrEmpty(workingDirectory)) {
			return null;
		}

		_taskExecutionInfo = _buildTaskExecutionInfo(project, workingDirectory, _taskName);

		if (_taskExecutionInfo == null) {
			return null;
		}

		ExternalSystemUtil.runTask(
			_taskExecutionInfo.getSettings(), _taskExecutionInfo.getExecutorId(), project, GradleConstants.SYSTEM_ID,
			new TaskCallback() {

				@Override
				public void onFailure() {
				}

				@Override
				public void onSuccess() {
					afterTask();
				}

			},

			getProgressMode(), true);

		RunnerAndConfigurationSettings configuration =
			ExternalSystemUtil.createExternalSystemRunnerAndConfigurationSettings(
				_taskExecutionInfo.getSettings(), project, GradleConstants.SYSTEM_ID);

		if (configuration == null) {
			return null;
		}

		return configuration;
	}

	protected void handleProcessStarted(
		@NotNull final String executorIdLocal, @NotNull final ExecutionEnvironment environmentLocal,
		@NotNull ProcessHandler handler) {

		if (!checkProcess(_taskExecutionInfo, executorIdLocal, environmentLocal)) {
			return;
		}

		refreshProjectView();
	}

	@Override
	protected void handleProcessTerminated(
		@NotNull final String executorIdLocal, @NotNull final ExecutionEnvironment environmentLocal,
		@NotNull ProcessHandler handler) {

		if (!checkProcess(_taskExecutionInfo, executorIdLocal, environmentLocal)) {
			return;
		}

		refreshProjectView();
	}

	private ExternalTaskExecutionInfo _buildTaskExecutionInfo(
		Project project, @NotNull String projectPath, @NotNull String fullCommandLine) {

		CommandLineParser gradleCmdParser = new CommandLineParser();

		GradleCommandLineOptionsConverter commandLineConverter = new GradleCommandLineOptionsConverter();

		commandLineConverter.configure(gradleCmdParser);

		ParsedCommandLine parsedCommandLine = gradleCmdParser.parse(ParametersListUtil.parse(fullCommandLine, true));

		try {
			Map<String, List<String>> optionsMap = commandLineConverter.convert(parsedCommandLine, new HashMap<>());

			List<String> systemProperties = optionsMap.remove("system-prop");

			String vmOptions =
				systemProperties == null ? "" : StringUtil.join(systemProperties, entry -> "-D" + entry, " ");

			String scriptParameters = StringUtil.join(
				optionsMap.entrySet(),
				entry -> {
					List<String> values = entry.getValue();
					String longOptionName = entry.getKey();

					if ((values != null) && !values.isEmpty()) {
						return StringUtil.join(values, entry1 -> "--" + longOptionName + ' ' + entry1, " ");
					}
					else {
						return "--" + longOptionName;
					}
				},
				" ");

			if (continuous()) {
				scriptParameters = scriptParameters + " --continuous";
			}

			ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();

			settings.setExternalProjectPath(projectPath);
			settings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.toString());
			settings.setScriptParameters(scriptParameters);
			settings.setTaskNames(parsedCommandLine.getExtraArguments());
			settings.setVmOptions(vmOptions);

			return new ExternalTaskExecutionInfo(settings, DefaultRunExecutor.EXECUTOR_ID);
		}
		catch (CommandLineArgumentException clae) {
			NotificationData notificationData = new NotificationData(
				"<b>Command-line arguments cannot be parsed</b>", "<i>" + _taskName + "</i> \n" + clae.getMessage(),
				NotificationCategory.WARNING, NotificationSource.TASK_EXECUTION);

			notificationData.setBalloonNotification(true);

			ExternalSystemNotificationManager externalSystemNotificationManager =
				ExternalSystemNotificationManager.getInstance(project);

			externalSystemNotificationManager.showNotification(GradleConstants.SYSTEM_ID, notificationData);

			return null;
		}
	}

	private ExternalTaskExecutionInfo _taskExecutionInfo;
	private final String _taskName;

}