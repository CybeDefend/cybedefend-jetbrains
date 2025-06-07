# CybeDefend IntelliJ IDEA Plugin

<div align="center">
  <img src="src/main/resources/icons/cybedefend.png" alt="CybeDefend Logo" width="128">
  
  **Enterprise-Grade Security Scanning Integrated into Your Development Environment**
  
  [![JetBrains Plugin](https://img.shields.io/badge/JetBrains-Plugin-orange.svg)](https://plugins.jetbrains.com/plugin/com.cybedefend.jetbrains)
  [![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ%20IDEA-2024.2+-blue.svg)](https://www.jetbrains.com/idea/)
  [![License](https://img.shields.io/badge/License-Proprietary-red.svg)](LICENSE)
</div>

## Overview

The CybeDefend IntelliJ IDEA Plugin integrates enterprise-grade security scanning capabilities directly into your development workflow. Designed for developers and security teams, this plugin provides comprehensive vulnerability detection and AI-powered security assistance without requiring you to leave your IDE.

## Key Features

### Multi-Type Security Scanning
Our plugin supports three comprehensive security analysis approaches:
- **Static Application Security Testing (SAST)** analyzes your source code to identify security vulnerabilities in real-time
- **Software Composition Analysis (SCA)** examines your project dependencies for known vulnerabilities and license compliance issues
- **Infrastructure as Code (IaC)** scanning validates your cloud infrastructure configurations against security best practices

### Unified Tool Window Interface
The plugin provides a consolidated view of all security findings through:
- Tabbed organization separating SAST, IaC, and SCA results for focused analysis
- Advanced severity filtering to prioritize critical, high, medium, and low severity vulnerabilities
- Direct navigation to vulnerable code locations with automatic line highlighting
- Real-time progress tracking with animated indicators during scan execution

### AI Security Assistant
Integrated artificial intelligence capabilities enhance your security workflow:
- Interactive chatbot provides contextual explanations of vulnerabilities
- Project-specific assistance tailored to your security findings
- Multi-turn conversations enable deep-dive security analysis and remediation planning

### Comprehensive Vulnerability Management
Each security finding includes detailed information to support remediation:
- Complete vulnerability descriptions with severity assessment and impact analysis
- Syntax-highlighted code snippets showing vulnerable sections
- Data flow analysis tracking how security issues propagate through your application
- Step-by-step remediation guidance with actionable recommendations

### Enterprise Configuration Management
Streamlined setup and team collaboration features:
- Secure API key management using IntelliJ's built-in password manager
- Multi-project and multi-organization support for enterprise environments
- Team-based vulnerability management and collaboration workflows

## Installation and Setup

### Installing the Plugin

**Method 1: JetBrains Marketplace (Recommended)**
1. Open IntelliJ IDEA and navigate to `File` → `Settings` → `Plugins`
2. Search for "CybeDefend" in the marketplace
3. Click `Install` and restart your IDE when prompted

**Method 2: Manual Installation**
1. Download the latest plugin release from our [GitHub releases page](https://github.com/cybedefend/cybedefend-jetbrains/releases)
2. Go to `File` → `Settings` → `Plugins` → gear icon → `Install Plugin from Disk...`
3. Select the downloaded `.zip` file and restart IntelliJ IDEA

### Initial Configuration

**API Key Setup**
1. Navigate to `File` → `Settings` → `Tools` → `CybeDefend`
2. Enter your CybeDefend platform API key in the provided field
3. Click `Apply` to save your configuration

**Project Configuration**
1. Enter your Project ID in the settings panel
2. The plugin will automatically validate your configuration and establish connection
3. Your settings are securely stored using IntelliJ's credential management system

**Starting Your First Scan**
1. Open the CybeDefend tool window located at the bottom of your IDE
2. Click the start scan button to initiate a comprehensive security analysis
3. Monitor progress through the animated indicator and status messages
4. Review results organized in separate tabs for each scan type

## Using the Plugin

### Security Scanning Workflow

**Initiating Scans**
The plugin automatically packages your project files and uploads them for analysis. Click the scan button in the CybeDefend tool window to begin. The system will display real-time progress as it archives your project, uploads files to our secure analysis platform, and processes your code.

**Reviewing Results**
Security findings are organized into three distinct categories accessible through dedicated tabs:
- **Static Analysis** displays source code vulnerabilities with detailed descriptions and remediation steps
- **Infrastructure as Code** shows configuration security issues in your deployment scripts and cloud configurations  
- **Software Composition Analysis** presents dependency vulnerabilities and license compliance information

Use the severity filter controls to focus on the most critical issues first. Each vulnerability listing includes severity indicators, affected file paths, and brief descriptions to help you prioritize remediation efforts.

**Investigating Vulnerabilities**
Click on any vulnerability to access comprehensive details including:
- Complete vulnerability description with severity assessment
- Clickable file paths that automatically open affected source files
- Highlighted vulnerable code sections with precise line indicators
- Data flow analysis showing how security issues propagate through your application
- Specific remediation recommendations and code examples

### AI-Powered Security Assistance

**Accessing the Security Assistant**
The CybeDefend Security Champion panel appears on the left side of your IDE. This AI-powered assistant provides contextual help and explanations for security findings in your project.

**Interactive Security Consultation**
- Select specific vulnerabilities from the dropdown menu to get targeted assistance
- Ask questions about security concepts, vulnerability patterns, or remediation approaches
- Engage in multi-turn conversations to explore complex security scenarios
- Use the "New" button to start fresh conversations when switching topics

**Getting Personalized Recommendations**
The AI assistant analyzes your specific project context to provide tailored security advice. Whether you need help understanding a particular vulnerability type or guidance on implementing security best practices, the assistant adapts its responses to your development environment and technology stack.

### Navigation and Code Integration

**Automatic Code Navigation**
When you click on file paths in vulnerability reports, the plugin automatically opens the relevant source files and positions your cursor at the exact location of security issues. Vulnerable lines are marked with distinctive highlighting to make identification immediate and clear.

**Code Context Awareness**
The plugin maintains awareness of your current editing context, ensuring that security information remains relevant as you navigate through your codebase. This integration helps maintain focus on security concerns while you implement fixes and improvements.

## Plugin Architecture

### Tool Window Configuration

**Main Security Dashboard**
The primary interface is anchored to the bottom panel of your IDE, providing comprehensive access to:
- Scan initiation and progress monitoring controls
- Tabbed vulnerability tables with advanced filtering capabilities  
- Detailed vulnerability inspection panel with comprehensive information display
- Direct access to plugin settings and configuration options

**AI Security Assistant Panel**
Located on the left side of your IDE, this panel offers:
- Interactive chatbot interface for security consultation
- Vulnerability-specific assistance and contextualized help
- Conversation management tools for organizing security discussions
- Context-aware guidance tailored to your project's security profile

### Configuration Management

**Settings Interface**
Access plugin configuration through `File` → `Settings` → `Tools` → `CybeDefend`

**Global Configuration Options:**
- **API Key Management**: Securely store your CybeDefend platform API key using IntelliJ's built-in credential management system

**Project-Specific Settings:**
- **Project ID Configuration**: Associate your workspace with specific CybeDefend projects
- **Organization Settings**: Configure team and organization-level preferences for collaborative security management

**Security and Authentication**
All credentials are stored using IntelliJ's secure password management infrastructure. The plugin automatically validates authentication status and project configurations to ensure seamless operation. Project-specific settings are maintained per workspace to support multi-project development environments.

## Supported Security Analysis Types

### Static Application Security Testing (SAST)
Comprehensive source code analysis covering:
- **Injection Vulnerabilities**: SQL injection, cross-site scripting, command injection, and LDAP injection attacks
- **Authentication and Session Management**: Weak authentication mechanisms, session fixation, and insecure session handling
- **Sensitive Data Exposure**: Hardcoded credentials, exposed API keys, and inadequate data encryption practices
- **Security Misconfiguration**: Insecure default configurations, error handling disclosure, and missing security headers
- **Additional Security Patterns**: Coverage includes over 100 security vulnerability patterns following OWASP guidelines

### Software Composition Analysis (SCA)
Dependency and third-party component security assessment:
- **Known Vulnerability Detection**: Comprehensive scanning against updated CVE databases and security advisories
- **License Compliance Analysis**: Open source license compatibility checking and compliance reporting
- **Dependency Management**: Identification of outdated components with upgrade recommendations
- **Transitive Dependency Analysis**: Deep scanning of indirect dependencies and their associated security risks

### Infrastructure as Code (IaC) Security
Cloud infrastructure configuration validation:
- **Multi-Cloud Support**: Security analysis for AWS, Microsoft Azure, and Google Cloud Platform configurations
- **Container Security**: Kubernetes pod security policies, RBAC configuration analysis, and container security best practices
- **Infrastructure Automation**: Terraform configuration security assessment and infrastructure hardening recommendations
- **Docker Security**: Container image security analysis and Dockerfile security best practice validation

## Privacy and Security Considerations

### Data Protection
CybeDefend prioritizes your code security and privacy through:
- **Local Processing**: Source code analysis occurs without permanent storage on external servers
- **Secure Transmission**: All data transfers use encrypted connections with industry-standard protocols
- **Minimal Data Collection**: Only essential metadata required for security analysis is transmitted to our platform

### Credential Security
- **Encrypted Storage**: API keys and authentication credentials are stored using IntelliJ's secure password management system
- **Memory Protection**: Credentials are handled securely in memory and cleared appropriately after use
- **Access Control**: Credential access is restricted to authorized plugin components only

## System Requirements

### Development Environment
- **IntelliJ IDEA**: Version 2024.2 or later required for full compatibility
- **Java Runtime**: Java 21 or later for optimal performance and security features  
- **Kotlin Support**: Kotlin 1.9.25 or later for plugin operation
- **Network Connectivity**: Internet connection required for security database updates and cloud analysis features

### Platform Account
- **CybeDefend Account**: Valid platform account with API key access required
- **Project Configuration**: Access to CybeDefend project management for workspace association
- **Team Permissions**: Appropriate organization and team permissions for collaborative features

## Support and Documentation

### Getting Help
For comprehensive assistance with the CybeDefend IntelliJ IDEA Plugin:
- **Documentation Portal**: Access detailed guides and tutorials at [https://docs.cybedefend.com](https://docs.cybedefend.com)
- **Technical Support**: Submit support requests and track resolution progress at [https://support.cybedefend.com](https://support.cybedefend.com)
- **Community Forum**: Connect with other users and share knowledge at [https://community.cybedefend.com](https://community.cybedefend.com)

### Troubleshooting Common Issues

**API Connection Problems**
If you experience connectivity issues, verify that your API key is correctly configured in the plugin settings. Ensure your network allows outbound HTTPS connections to the CybeDefend platform. Check that your API key has appropriate permissions for your organization and projects.

**Scan Failures**
Failed scans typically result from project configuration issues or insufficient permissions. Verify that your Project ID is correctly set and that your account has access to the specified project. Large projects may require additional processing time.

**Performance Considerations**
The plugin is optimized for projects of various sizes, but very large monorepos containing more than 10,000 files may experience longer scan times. Network connectivity speed can also impact upload and download performance during scan operations.

**Diagnostic Information**
For technical support, IntelliJ's log files contain detailed error information that can help diagnose issues. Access logs through `Help` → `Show Log in Explorer` to provide comprehensive diagnostic data when contacting support.

### Known Limitations
- Large monorepos may require extended scan processing time
- Real-time scanning features require active network connectivity  
- Some vulnerability types may require specific programming language support
- Advanced features may require specific organization tier access

## License and Legal

### Software License
This plugin is proprietary software developed and owned by CybeDefend. Use of this software is subject to the terms and conditions specified in your CybeDefend platform agreement. The plugin is provided exclusively for use with valid CybeDefend platform subscriptions.

### Copyright Notice
Copyright © 2024 CybeDefend. All rights reserved. No part of this software may be reproduced, distributed, or transmitted in any form or by any means without the prior written permission of CybeDefend.

### Third-Party Components
This plugin incorporates open-source components and libraries that are subject to their respective licenses. A complete list of third-party components and their licenses is available in the plugin distribution.

### Data Processing and Privacy
Use of this plugin is subject to CybeDefend's Privacy Policy and Data Processing Agreement. Security scanning involves the transmission of code metadata to CybeDefend's analysis platform for processing. No source code is permanently stored on external servers, and all data transmission occurs over encrypted connections.

### Support and Warranty
Technical support for this plugin is provided exclusively to valid CybeDefend platform subscribers. Support terms and service level agreements are defined in your CybeDefend platform subscription agreement.