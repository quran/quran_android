**Notes and Things to Fix**

 * ProgressBars in Activity - appcompat broke these basically, so we have to
  figure out what to do here (either "fix it" by providing a different layout
  which contains the needed progress views, or find a different way to
  represent progress) - it probably makes sense to change it, especially due
  to both the way material design is moving.
 * Search - the panel doesn't look new. Can we fix this? should see [this
     article](https://chris.banes.me/2014/10/17/appcompat-v21/)
 * cleanup about (remove abs, and ideally, cleanup the strings, code, etc)
 * QuranActivity, TranslationManagerActivity, etc have config changes. they
   shouldn't. This may break dialogs.
 * fix transparent toolbar in kitkat and l - temporarily turned it off for
   kitkat due to some issues with the ToolBar in landscape mode and resorted
   to using statusBarColor on lollipop (which breaks the ToolBar animation).
   will need to revisit this and fix it properly.
 * move sura/ayah overlay drawing into the QuranImagePageLayout ViewGroup
