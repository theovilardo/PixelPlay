# 🍴 Forking Guide: Creating Your Own Version

This guide will help you fork PixelPlayer and create your own version with online music functionality.

## 📋 Prerequisites

- GitHub account
- Git installed locally
- Android Studio
- Basic knowledge of Git and GitHub

## 🚀 Step-by-Step Forking Process

### 1. Fork the Repository
1. Go to the original repository: https://github.com/theovilardo/PixelPlayer
2. Click the "Fork" button in the top-right corner
3. Choose your GitHub account as the destination
4. Wait for the fork to complete

### 2. Clone Your Fork
```bash
# Clone your forked repository
git clone https://github.com/YOUR_USERNAME/PixelPlayer.git

# Navigate to the project directory
cd PixelPlayer

# Add the original repository as upstream (to keep up-to-date)
git remote add upstream https://github.com/theovilardo/PixelPlayer.git
```

### 3. Configure Your Identity
```bash
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"
```

### 4. Create a New Branch for Your Changes
```bash
# Create and switch to a new branch
git checkout -b feature/online-music-enhancement
```

### 5. Make Your Changes
- Modify the app name, package name, and branding
- Update the README with your features
- Add your online music functionality
- Test thoroughly

### 6. Commit Your Changes
```bash
# Add all changes
git add .

# Commit with a descriptive message
git commit -m "Add online music streaming functionality

- Integrated YouTube search via NewPipe
- Added Piped API for privacy-friendly streaming
- Enhanced Deezer integration for artist images
- Updated UI for online content discovery
- Improved performance optimizations"
```

### 7. Push to Your Fork
```bash
# Push to your fork's branch
git push origin feature/online-music-enhancement
```

### 8. Create a Pull Request (Optional)
If you want to contribute back to the original project:
1. Go to your fork on GitHub
2. Click "New Pull Request"
3. Choose your branch
4. Fill in the PR description
5. Submit

## 🔧 Essential Changes to Make

### 1. Update App Identity
- Change app name in `strings.xml`
- Update package name in `build.gradle.kts`
- Modify app icon and branding
- Update the application ID

### 2. Update Repository Information
- Change repository URLs in README
- Update author information
- Modify links and badges
- Add your own licensing if desired

### 3. Configure API Keys
- Add your own API keys if needed
- Update API endpoints
- Configure authentication

## 🔄 Keeping Your Fork Updated

To sync with the original repository:
```bash
# Fetch latest changes from upstream
git fetch upstream

# Switch to main branch
git checkout main

# Merge upstream changes
git merge upstream/main

# Push updates to your fork
git push origin main
```

## ⚠️ Important Notes

- **License**: Respect the original MIT license
- **Credits**: Give credit to the original author
- **Support**: You're responsible for supporting your fork
- **APIs**: Ensure you have proper API usage rights

## 📱 Publishing Your App

If you plan to publish to app stores:
1. Change the package name completely
2. Update the app ID and signing keys
3. Ensure all API keys are your own
4. Test thoroughly on multiple devices
5. Follow store guidelines

## 🤝 Community and Support

- Create issues in your fork for bug tracking
- Set up discussions for user feedback
- Provide clear documentation
- Consider creating a website or social media presence

---

**Remember**: A fork is your responsibility. Maintain it, update it, and support your users!
