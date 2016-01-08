#! /bin/bash

# a shell script to make it easier to download images from google's
# material design icons project.
#
# usage:
# sh ~/material.sh -f ic_pause.png https://github.com/google/material-design-icons/blob/master/av/drawable-xxhdpi/ic_pause_white_24dp.png
# by default, it outputs to app/src/main/res/drawable-* in the current
# directory, but you can override with -o.
#
# note that the url can be the raw url or can be the web url, and that
# the density bucket doesn't matter (as long as it's drawable-something,
# the script should do the right thing).

outdir=app/src/main/res/

while getopts ":f:o:h:" opt; do
   case $opt in
      f)
      filename=$OPTARG
      ;;
      o)
      outdir=$OPTARG
      ;;
      h)
      help=true
      ;;
   esac
done
shift $((OPTIND-1))

if [ "$help" = true ] || [ $# -lt 1 ];
then
  echo "usage: $0 [options] url"
  echo "   -f foo.png    rename file to foo.png instead of remote name"
  echo "   -o /path      use custom path instead of app/src/main/res/"
  exit
fi


url=${@/github.com/raw.githubusercontent.com}
url=${url/blob\/}
baseurl="${url%%/drawable*}"
srcname="${url##*/}"

if [ -z "${filename}" ]; then
   filename=$srcname
fi

echo $url
for arg in mdpi hdpi xhdpi xxhdpi xxxhdpi
do
   desturl=$baseurl/drawable-$arg/$srcname
   curl $desturl -o $outdir/drawable-$arg/$filename
done
