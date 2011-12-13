<?php
$schema_version = 2;

if (!isset($argv[2]))
   die("usage: {$argv[0]} output_name tanzil_tbl_name [version]\n");
$colname = $argv[1];
$tbl = $argv[2];

$version = 1;
if (isset($argv[3]) && is_numeric($argv[3]))
   $version = $argv[3];

echo 'mysql password: ';
system('stty -echo');
$password = trim(fgets(STDIN));
system('stty echo');
echo "\n";

// connect to mysql
mysql_connect('localhost', 'quran', $password);
mysql_select_db('tanzil');
mysql_query("set names 'utf8'") or die('could not query: ' . mysql_error());

// get a sqlite database
$dbname = "$colname";
$sqlite = new SQLite3($dbname);

// properties table
$q = 'create table properties( property text, value text );';
$sqlite->exec($q);
$q = 'insert into properties(property, value) values' .
   "('schema_version', '$schema_version')";
$sqlite->exec($q);
$q = 'insert into properties(property, value) values' .
   "('text_version', '$version')";
$sqlite->exec($q);

// verses table
$q = 'create virtual table verses using fts3( sura integer, ayah integer, ' .
   'text text, primary key(sura, ayah ) );';
$sqlite->exec($q);

// get the verses
$q = "select sura, aya, text from $tbl";
$res = mysql_query($q) or die('could not query: ' . mysql_error());
while ($row = mysql_fetch_assoc($res)){
   $sura = $row['sura'];
   $ayah = $row['aya'];
   $text = $sqlite->escapeString($row['text']);
   $q = "insert into verses(sura, ayah, text) values($sura, $ayah, '$text')";
   $sqlite->exec($q);
}

// close stuff
$sqlite->close();
mysql_close();
