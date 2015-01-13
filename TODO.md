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


QuranPageLayout is functionally equivalent to the previous layouts, with the
following exceptions:
1. portrait images were wrap/wrap, now they are match/match
2. translations were match/wrap, now they are match/match

Should consider which of those values is more "correct," while noting that
changing portrait images to wrap/wrap would require an adjustment in terms
of where the overlay page text is drawn.
