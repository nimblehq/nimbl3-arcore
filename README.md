# Nimbl3 - ARCore sample project
Exploring AR in Android with ARCore

This project is forked from the original sample project from [Google](https://github.com/google-ar/arcore-android-sdk.git) with modifications, more objects added and simple drag interaction with object in AR Environment.

## Setup:
- Currently the ARCore supported devices list is [very limited](https://developers.google.com/ar/discover/#supoporteddevices), so in order to try it on devices that are not on the list, we need to use the `arcore_for_all_client.aar` lib (already setup here). Credit to @tomthecarrot's [github](https://github.com/tomthecarrot/arcore-for-all).

- If you're holding a supported phone, simply switch it to `arcore_client.aar` in our app's gradle file to use the original support library from google.

- Also, another step is required for all phones to be able to run this project is that you need to install the [Tango service](https://github.com/google-ar/arcore-android-sdk/releases/download/sdk-preview/arcore-preview.apk) (which is currently called as ar-preview sdk). Find out more from Google's instructions [here](https://developers.google.com/ar/discover/).

- PR is always welcome.

## License

This project is Copyright (c) 2014-2018 Nimbl3 Ltd. It is free software,
and may be redistributed under the terms specified in the [LICENSE] file.

[LICENSE]: /LICENSE

## About

![Nimbl3](https://dtvm7z6brak4y.cloudfront.net/logo/logo-repo-readme.jpg)

This project is maintained and funded by Nimbl3 Ltd.

We love open source and do our part in sharing our work with the community!
See [our other projects][community] or [hire our team][hire] to help build your product.

[community]: https://nimbl3.github.io/
[hire]: https://nimbl3.com/
