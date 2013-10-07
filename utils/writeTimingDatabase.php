<?php
$ayahs = array(
   7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111,
   43, 52, 99, 128, 111, 110, 98, 135, 112, 78, 118, 64, 77,
   227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75,
   85, 54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55,
   78, 96, 29, 22, 24, 13, 14, 11, 11, 18, 12, 12, 30, 52, 52,
   44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25,
   22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
   11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6
);

if (!isset($argv[1])){
   die("usage: {$argv[0]} dbname\n");
}

$qariname = $argv[1];
$sqlite = new SQLite3($qariname);
$q = 'create table timings(sura int, ayah int, time int, ' .
   'primary key(sura, ayah));';
$sqlite->exec($q);

for ($i=1; $i<=114; $i++){
   $file = '';
   if ($i < 10){ $file = "00$i.txt"; }
   else if ($i < 100){ $file = "0$i.txt"; }
   else { $file = "$i.txt"; }

   $ayah = 1;
   $lines = file($file);
   $total = count($lines);
   foreach ($lines as $l){
      if ($ayah > $ayahs[$i-1]){ $ayah = 999; }
      $l = rtrim($l);
      $q = "insert into timings values($i, $ayah, $l);";

      $sqlite->exec($q);
      $ayah++;
   }
}
