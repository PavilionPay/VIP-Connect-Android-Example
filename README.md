# VIP Connect SDK Integration for Android

This repository contains a sample application that demonstrates integration and use of the VIP Connect SDK for Android.

More instructions about the VIP Connect SDK can be found in our [main documentation](https://developer.vippreferred.com/).

## Integration Requirements

To interact with the VIP Connect services, first setup an [operator](https://developer.vippreferred.com/operator-onboarding/operator-setup) with Pavilion Payments.
This will allow you to create an [authentication token](https://developer.vippreferred.com/integration-steps/operator-requirements) for use with the Pavilion APIs
to [create a patron session](https://developer.vippreferred.com/APIS/SDK/create-patron-session) for your customer. 

After getting a session id, [launch the VIP SDK web component via URL](https://developer.vippreferred.com/integration-steps/invoke-web-component) inside
an [Auth Tab](https://developer.chrome.com/docs/android/custom-tabs/guide-auth-tab) (shown in `MainActivity.kt`). An Auth Tab is a browser tab made for
sign-in style flows: your app starts it with a url and a redirect scheme, and the browser hands the result back to your app. It is the Android
counterpart of `ASWebAuthenticationSession` on iOS.

The sample runs the Auth Tab in private (incognito) mode by calling `setEphemeralBrowsingEnabled(true)` when the patron's browser supports
[ephemeral browsing](https://developer.chrome.com/docs/android/custom-tabs/guide-ephemeral-tab). The tab then shares no cookies or website data with
the browser or with earlier sessions. Browsers that do not support it open a normal tab.

This demo app uses Composable functions to create its UI, but Compose is not a requirement to use the VIP SDK; you may launch an Auth Tab through any
method appropriate for your app.

VIP Connect uses [Finicity Connect WebSDK](https://developer.mastercard.com/open-finance-us/documentation/connect/integrating/sdk/web-sdk/sdk/) or [Plaid Hosted Link](https://plaid.com/docs/link/hosted-link/) to securely connect your customer's bank accounts with VIP Connect; launching
the VIP Connect SDK inside an Auth Tab (in private mode, as described above) is necessary to provide the best experience to your customers.

### WebViews are not supported

Do not host VIP Connect in an embedded WebView (`android.webkit.WebView`). It is not supported for native Android apps. When a patron adds a bank account, they sign
in at their bank through OAuth, and that sign-in must not run inside an embedded WebView:

- **Finicity (Mastercard Data Connect):** Mastercard's [integration guide](https://developer.mastercard.com/open-finance-us/documentation/connect/integrating/non-sdk/non-sdk-android/) says the bank's OAuth session must open in a secure container
  "as we don't support WebViews for OAuth process".
- **Plaid:** Plaid's [WebView page](https://plaid.com/docs/link/webview/) says "Using webviews to present Link is deprecated", and its
  [changelog](https://plaid.com/docs/changelog/) says WebView-based integrations are no longer allowed for new Plaid customers (April 2025).
- **Banks restrict it.** Both providers name Chase as a bank that restricts sign-in from WebViews.

An Auth Tab, and the Custom Tab it falls back to, run in the patron's browser, not in an embedded WebView, so bank sign-in works in them.

## Returning to the app from VIP Connect

Upon completion or cancellation, VIP Connect will navigate to the address at the `returnURL` param passed during [session creation](https://developer.vippreferred.com/APIS/SDK/create-patron-session).
The sample app uses `closevip://done` as an example `returnURL`, which your app may use or modify as needed.

The scheme of the `returnURL` (`closevip`) is passed to the Auth Tab as its redirect scheme. When VIP Connect navigates to the `returnURL`, the Auth Tab
closes and your app receives the result in the `ActivityResultCallback` registered with `AuthTabIntent.registerActivityResultLauncher`:

| Result code | Meaning |
|---|---|
| `AuthTabIntent.RESULT_OK` | VIP Connect navigated to the `returnURL`; the session has ended. `resultUri` holds the full url |
| `AuthTabIntent.RESULT_CANCELED` | The patron closed the tab. The transaction may still have completed if they closed it at the end |

If the patron's browser does not support Auth Tab, the same call opens a Custom Tab instead. The `returnURL` then comes back to the app through
the intent filter for the `closevip` scheme in `AndroidManifest.xml`, which `MainActivity` handles in `onNewIntent`. The scheme in the manifest must
match the scheme of your `returnURL`.

The scheme of the `returnURL` must be the same in all three of these places. If one differs, the tab will not close by itself.

| Place | In the sample |
|---|---|
| The `returnURL` field of the create-session request | `RETURN_URL` in `VIPSessionUrlViewModel.kt` |
| The redirect scheme given to the Auth Tab | `RETURN_SCHEME`, passed to `AuthTabIntent.launch(...)` in `MainActivity.kt` |
| The intent filter in `AndroidManifest.xml` | `<data android:scheme="closevip" />`, on an activity declared `singleTask` |

You do not need App Links or a Digital Asset Links file to run the sample.

## How a session ends

Your app is told one of two things: the `returnURL` was reached, or the patron closed the browser session. Neither one tells you whether a
transaction was made.

| What the patron does | What VIP Connect does | What your app receives |
|---|---|---|
| Completes the transaction | Navigates to your `returnURL` | `AuthTabIntent.RESULT_OK`, with the url in `resultUri` |
| Taps **Cancel Deposit**, then **Yes** | Shows a "Logged Out" page with a **Continue** button, and stays there | Nothing yet |
| Then taps **Continue** | Navigates to the same `returnURL` | The same result as a completed transaction |
| Closes the tab | Nothing | `AuthTabIntent.RESULT_CANCELED` |

- **Reaching the `returnURL` means the session ended, not that money moved.** A cancelled deposit followed by **Continue** looks the same as a
  completed one, which is why the sample says "session ended".
- **A closed session can still have a completed transaction**, if the patron closed it after finishing instead of waiting for the return.
- **Confirm the outcome on your server** before you update a balance or show a success message. Please contact your Pavilion Payments
  representative for the right way to do this for your integration.

When the browser falls back to a Custom Tab, the `returnURL` arrives as an intent (`onNewIntent`) instead of `RESULT_OK`. Closing the tab still gives
`RESULT_CANCELED`.

## Running the sample app

The sample launches the VIP Connect flow as a demonstration. It uses fixed test data to create a real session on your operator, so it needs your
operator's values before it can run.

### What you need

- [Android Studio](https://developer.android.com/studio), and an Android phone or emulator running Android 8.0 (API 26) or later with Chrome installed.
- From [operator setup](https://developer.vippreferred.com/operator-onboarding/operator-setup): your operator's **JWT Secret** and **JWT Issuer**,
  and the name of the test environment your operator is in (such as `cert`). Please contact your Pavilion Payments representative if you need help
  obtaining these values.
- A test patron that exists for your operator (see [The test patron](#the-test-patron)).

### Steps

1. Clone this repository and open it in Android Studio.
2. Open `local.properties` in the project root (Android Studio creates it; create it if it is missing) and add three lines:

   ```
   vip.jwtSecret=<your operator's JWT Secret, exactly as provided (a Base64 string)>
   vip.jwtIssuer=<your operator's JWT Issuer>
   vip.environment=<the test environment name, such as cert>
   ```

   The app calls `https://<vip.environment>.api-gaming.paviliononline.io/sdk`. `local.properties` is not checked in, so your values stay out of
   source control.
3. If you are not using the built-in test patron, replace its values in `VIPSessionUrlViewModel.kt` (see [The test patron](#the-test-patron)).
4. Choose your phone or emulator and run the `app` configuration.
5. On the home screen, tap **Launch VIP SDK**. VIP Connect opens in an Auth Tab.
6. Complete or cancel the transaction in VIP Connect. When VIP Connect navigates to `closevip://done`, the tab closes by itself and the home screen
   shows "VIP Connect session ended." VIP Connect uses the same `returnURL` after a finished transaction and after a cancelled one, so this
   message does not by itself mean a transaction was made.

### If it doesn't start

| What you see | Cause |
|---|---|
| "Session Creation Failed" | The three values are missing from `local.properties`, the API rejected them, or the test patron does not exist for your operator. Rebuild after changing `local.properties` |

### The test patron

The sample creates a session for one fixed, existing patron. The values are in `defaultPreferredUser` in `VIPSessionUrlViewModel.kt`:

| Field | Value |
|---|---|
| `patronId` | `cb7c887d-6687-4aa5-a664-31cf6c810df7` |
| `vipCardNumber` | `7210806973` |
| `dateOfBirth` | `04/15/1980` |

The session is created with [`patronsession/existing`](https://developer.vippreferred.com/APIS/SDK/create-patron-session), so the patron must
already exist for your operator in the environment you chose. If this patron does not exist for your operator, replace the three values with a
patron of your own. The sample does not enroll new patrons.

`toPatronRequest` in the same file sets the rest of the request: a 10.00 deposit, a new `transactionID` for every session, `productType` `0` and the
`returnURL` `closevip://done`.

## Building your own app

The sample is a starting point for the browser-session code, not for the session-creation code.

While this sample app creates its JWT token locally and accesses VIP Mobility APIs directly, this is NOT a recommended practice for production apps!
Your app should not have access to your operator secrets, and should use a more secure method of obtaining a session id such as getting it from a backend
service that holds the secret values and acts as a middle layer between the app and VIP Connect's APIs.

Before you ship:

- **Move token signing and session creation to your backend.** It holds the operator secret, calls `patronsession/existing`, and returns only the
  session id to the app.
- **Send real patron and transaction data** in place of the built-in patron, the fixed 10.00 amount and the generated `transactionID`.
- **Choose your own `returnURL` scheme**, unique to your app, and change it in every place listed under
  [Returning to the app from VIP Connect](#returning-to-the-app-from-vip-connect).
- **Keep the browser session and private mode.** Do not replace it with a WebView.
- **Point at your production environment** once your operator is set up there.

What you can copy as-is: `authTabLauncher`, `launchVipConnect` and `handleReturnUrl` in `MainActivity.kt`, plus the intent filter and the `singleTask` setting in
`AndroidManifest.xml`.
