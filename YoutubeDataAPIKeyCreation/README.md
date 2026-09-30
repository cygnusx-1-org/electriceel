# YoutubeDataAPIKeyCreation

This is a golang binary that uses browser based OAuth authentication to login to your Google account to create a YouTube Data API key for use with the Settings | Content Filtering | Hide Content | Home categories feature.

The feature needs the YouTube Data API to pull categorization data about videos in your Home feed to hide them. Uploaders often miscategorize their videos, but YouTube has their own generated categorization data that is more accurate. Accuracy is important for good filtering.

## Usage

./ydakc

It will output the URL to the screen that you need to go to do the OAuth authentication after attempting to automatically open it. Once your Google account is authenticated in the browser it will automatically generate a 32 character API key, and display it. I recommend storing this in your password manager of choice.

I recommend using [LocalSend](https://localsend.org/) from the system you run ydakc from to share it with LocalSend running on your Android TV device. This will let you get the string copied to the Android TV device, you can copy it, and then paste it into Settings | System | General  | API keys | YouTube Data API key.

[LocalSend](https://localsend.org/) Windows, macOS, Linux, Android(it is in the Google Play Store), and iOS. I don't recommend the Web version.
