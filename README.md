# CybeDefend IntelliJ IDEA Plugin

<div align="center">
  <img src="src/main/resources/icons/cybedefend.png" alt="CybeDefend Logo" width="128">
  
  **Comprehensive Security Scanning Directly in Your IDE**
  
  [![JetBrains Plugin](https://img.shields.io/badge/JetBrains-Plugin-orange.svg)](https://plugins.jetbrains.com/plugin/com.cybedefend.jetbrains)
  [![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ%20IDEA-2024.2+-blue.svg)](https://www.jetbrains.com/idea/)
  [![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
</div>

## Overview

The CybeDefend IntelliJ IDEA Plugin brings enterprise-grade security scanning directly into your development workflow. Seamlessly integrated with IntelliJ IDEA, this plugin provides comprehensive vulnerability detection and AI-powered security assistance without leaving your IDE.

## ✨ Key Features

### 🔍 **Multi-Type Security Scanning**
- **SAST (Static Application Security Testing)** - Analyze source code for security vulnerabilities
- **SCA (Software Composition Analysis)** - Scan dependencies for known vulnerabilities  
- **IaC (Infrastructure as Code)** - Security analysis for cloud infrastructure configurations

### 🛠️ **Unified Tool Window**
- **Tabbed Interface** - Organized results by scan type (SAST, IaC, SCA)
- **Severity Filtering** - Filter vulnerabilities by severity level (Critical, High, Medium, Low, Info)
- **One-Click Navigation** - Jump directly to vulnerable code locations with line highlighting
- **Real-time Progress** - Live scan status with animated progress indicators

### 🤖 **AI Security Assistant**
- **ChatBot Integration** - Ask questions about vulnerabilities and get AI-powered explanations
- **Context-Aware Assistance** - Get help specific to your project's vulnerabilities
- **Interactive Conversations** - Multi-turn conversations for deeper security insights

### 📊 **Detailed Vulnerability Information**
- **Comprehensive Details** - Full vulnerability descriptions, severity, and impact analysis
- **Code Snippets** - View vulnerable code sections with syntax highlighting
- **Data Flow Analysis** - Track how data flows through vulnerable paths (SAST)
- **Remediation Guidance** - Step-by-step instructions to fix vulnerabilities

### ⚙️ **Seamless Configuration**
- **API Key Management** - Secure credential storage using IntelliJ's password manager
- **Project Configuration** - Easy setup for multiple projects and organizations
- **Team Collaboration** - Support for team-based vulnerability management

## 🚀 Getting Started

### Installation

1. **From JetBrains Marketplace** (Recommended)
   - Open IntelliJ IDEA
   - Go to `File` → `Settings` → `Plugins`
   - Search for "CybeDefend"
   - Click `Install` and restart IDE

2. **Manual Installation**
   - Download the latest release from the [GitHub releases page](https://github.com/cybedefend/cybedefend-jetbrains/releases)
   - Go to `File` → `Settings` → `Plugins` → `⚙️` → `Install Plugin from Disk...`
   - Select the downloaded `.zip` file and restart

### Initial Setup

1. **Configure API Key**
   - Go to `File` → `Settings` → `Tools` → `CybeDefend`
   - Enter your CybeDefend API key
   - Click `Apply`

2. **Set Up Project**
   - Enter your Project ID in the settings
   - The plugin will automatically validate your configuration

3. **Start Scanning**
   - Open the CybeDefend tool window at the bottom of your IDE
   - Click the ▶️ "Start Scan" button
   - Results will appear in organized tabs

## 🎯 Usage

### Running Security Scans

1. **Start a Scan**
   - Click the ▶️ button in the CybeDefend tool window
   - The plugin will scan your entire project
   - Progress is shown with an animated indicator

2. **View Results**
   - Results are organized in three tabs: Static Analysis, Infrastructure as Code, and Software Composition Analysis
   - Use severity filters to focus on critical issues
   - Total vulnerability count is displayed in the status bar

3. **Investigate Vulnerabilities**
   - Click on any vulnerability to view detailed information
   - The details panel shows:
     - Severity badge and vulnerability name
     - File location (clickable to navigate)
     - Full description and remediation steps
     - Code snippets and data flow analysis
     - Historical information and metadata

### Using the AI Assistant

1. **Open the ChatBot**
   - The "CybeDefend Security Champion" panel appears on the left side
   - Select a vulnerability from the dropdown (optional)
   - Type your security questions

2. **Get AI-Powered Help**
   - Ask questions about specific vulnerabilities
   - Get explanations about security concepts
   - Receive personalized remediation advice
   - Start new conversations anytime with the "New" button

### File Navigation and Code Highlighting

- **Auto-Navigation**: Click vulnerability file paths to automatically open files
- **Line Highlighting**: Vulnerable lines are highlighted with red underscores
- **Context Awareness**: Editor opens at the exact vulnerable location

## 🔧 Tool Windows

### Main Dashboard (Bottom Panel)
- **Location**: Anchored to the bottom of the IDE
- **Features**: 
  - Scan controls (Start, Clear Results)
  - Vulnerability tables with filtering
  - Detailed vulnerability viewer
  - Settings access

### AI Assistant (Left Panel)  
- **Location**: Anchored to the left side of the IDE
- **Features**:
  - Interactive chatbot
  - Vulnerability-specific assistance
  - Conversation management
  - Context-aware help

## ⚙️ Configuration

### Settings Panel
Access via `File` → `Settings` → `Tools` → `CybeDefend`

**Global Settings:**
- **API Key**: Your CybeDefend platform API key (securely stored)

**Project Settings:**
- **Project ID**: Unique identifier for your project
- **Organization**: Team/organization configuration

### Authentication
- API keys are securely stored using IntelliJ's built-in password manager
- Automatic validation ensures credentials are working
- Project configurations are stored per-workspace

## 📋 Supported Vulnerability Types

### SAST (Static Analysis)
- **Injection Flaws**: SQL injection, XSS, command injection
- **Authentication Issues**: Weak authentication, session management
- **Sensitive Data Exposure**: Hardcoded secrets, data leaks
- **Security Misconfiguration**: Insecure defaults, error handling
- **And 100+ other security patterns**

### SCA (Dependency Analysis)
- **Known CVEs**: Comprehensive vulnerability database
- **License Compliance**: Open source license analysis
- **Outdated Dependencies**: Update recommendations
- **Transitive Dependencies**: Deep dependency tree analysis

### IaC (Infrastructure as Code)
- **Cloud Misconfigurations**: AWS, Azure, GCP security issues
- **Kubernetes Security**: Pod security, RBAC issues
- **Terraform**: Infrastructure security best practices
- **Docker**: Container security analysis

## 🛡️ Security and Privacy

- **Secure Communication**: All API calls use HTTPS encryption
- **Local Storage**: No source code is stored on external servers
- **Credential Management**: API keys stored using IntelliJ's secure password manager
- **Privacy First**: Only necessary metadata is transmitted for analysis

## 📝 Requirements

- **IntelliJ IDEA**: 2024.2 or later
- **Java**: 21 or later
- **Kotlin**: 1.9.25 or later
- **CybeDefend Account**: Valid API key required

## 🆘 Support and Documentation

### Getting Help
- **Documentation**: [https://docs.cybedefend.com](https://docs.cybedefend.com)
- **Support Portal**: [https://support.cybedefend.com](https://support.cybedefend.com)
- **Community Forum**: [https://community.cybedefend.com](https://community.cybedefend.com)

### Troubleshooting
- **API Connection Issues**: Verify your API key in settings
- **Scan Failures**: Check project configuration and permissions
- **Performance**: Large projects may take longer to scan
- **Log Files**: Check IntelliJ's log files for detailed error information

### Known Issues
- Large monorepos (>10,000 files) may experience slower scan times
- Network connectivity required for real-time scanning
- Some vulnerability types may require specific language support

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details.

### Development Setup
1. Clone the repository
2. Open in IntelliJ IDEA
3. Ensure Java 21+ is configured
4. Run `./gradlew runIde` to start a development instance

### Building from Source
```bash
./gradlew buildPlugin
```
The built plugin will be in `build/distributions/`

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 📬 Contact

- **Website**: [https://www.cybedefend.com](https://www.cybedefend.com)
- **Email**: support@cybedefend.com
- **Twitter**: [@CybeDefend](https://twitter.com/cybedefend)

---

<div align="center">
  <p>Made with ❤️ by the CybeDefend Team</p>
  <p>Securing code, one vulnerability at a time</p>
</div>
