# GitHub Repository Secrets Setup

To enable the CI/CD pipeline with email notifications, you need to add the following secrets to your GitHub repository.

## How to Add Secrets

1. Go to your GitHub repository
2. Click on **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Add each secret below

---

## Required Secrets

### SMTP Configuration (Gmail)

Since you're sending from `yoshninjas.1@gmail.com`, you'll need to use Gmail's SMTP server with an App Password (not your regular Gmail password).

#### 1. SMTP_SERVER
- **Value**: `smtp.gmail.com`
- **Description**: Gmail SMTP server address

#### 2. SMTP_PORT
- **Value**: `587`
- **Description**: Gmail SMTP port (TLS)

#### 3. SMTP_USERNAME
- **Value**: `yoshninjas.1@gmail.com`
- **Description**: Your Gmail address

#### 4. SMTP_PASSWORD
- **Value**: `<Your Gmail App Password>`
- **Description**: Gmail App Password (NOT your regular password)
- **How to generate**:
  1. Go to https://myaccount.google.com/apppasswords
  2. Sign in with your Gmail account
  3. Select "Mail" and "Other (Custom name)"
  4. Name it "GitHub Actions CI"
  5. Click "Generate"
  6. Copy the 16-character password (remove spaces)
  7. Use this as the SMTP_PASSWORD value

#### 5. EMAIL_TO
- **Value**: `email1@example.com,email2@example.com`
- **Description**: Comma-separated list of recipient email addresses
- **Example**: `john.doe@company.com,jane.smith@company.com`
- **Note**: Replace with your actual two email addresses

---

## Summary of Secrets to Add

| Secret Name      | Value                                    |
|------------------|------------------------------------------|
| SMTP_SERVER      | smtp.gmail.com                           |
| SMTP_PORT        | 587                                      |
| SMTP_USERNAME    | yoshninjas.1@gmail.com                   |
| SMTP_PASSWORD    | (Your Gmail App Password)                |
| EMAIL_TO         | (Your two comma-separated email addresses) |

---

## CI/CD Workflow Changes

The updated workflow (`api-test-ci.yml`) now:

✅ Triggers only on the `develop` branch (push and pull requests)
✅ Removed Slack notifications
✅ Sends email notifications to the addresses specified in `EMAIL_TO`
✅ Includes Allure report link hosted on GitHub Pages in the email
✅ Uses `yoshninjas.1@gmail.com` as the sender address

---

## Email Notification Content

The email will include:
- Test status (PASS ✅ or FAIL ❌)
- Test summary (Total, Passed, Failed, Errors, Skipped)
- Failed test details (if any)
- Repository and branch information
- GitHub Actions run link
- 📊 Allure Report link (hosted on GitHub Pages)

---

## Testing the Setup

After adding all secrets:

1. Push a commit to the `develop` branch
2. Check GitHub Actions tab to see the workflow running
3. Verify that emails are received at both addresses
4. Check that the Allure report link in the email works

---

## Troubleshooting

### Email not received?
- Verify all SMTP secrets are correct
- Check spam/junk folders
- Ensure Gmail App Password is generated correctly
- Verify `EMAIL_TO` has valid email addresses

### Gmail App Password not working?
- Make sure 2-Factor Authentication is enabled on your Gmail account
- App Passwords only work with 2FA enabled
- Generate a new App Password if the old one doesn't work

### Workflow not triggering?
- Ensure you're pushing to the `develop` branch
- Check GitHub Actions tab for any errors
- Verify the workflow file is in `.github/workflows/` directory
