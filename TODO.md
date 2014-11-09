**Notes and Things to Fix**

 * `IcsSpinner`, which we used in ActionBarSherlock, is gone. there is one in
  appcompat, but it's private. we need to replace it or figure out how to
  otherwise fix it (otherwise, it'll look terrible pre-ICS).
 * Themes - previously, we were doing `setTheme` in `onCreate` - we should have
  a set of themes and declare them in the manifest directly.
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
