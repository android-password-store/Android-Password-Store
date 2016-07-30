{-
  oisafe2psore - Quick and dirty script to convert OI Safe export CSV
                 into the password-store tree format.

  Copyright 2016 Eugene Crosser

  License: BSD, Apache or GPLv3 - chose whatever suits you.

  You will need to adjust paths to the GnuPG program and the CSV
  file produced by OI Safe. Also fill in the PGP key I.D.
  Description becomes the file name. '*' in the Description is
  converted to '+', '/' - to '-'. If this is not sufficient,
  adjust the function `sanitize`.
-}

{-# LANGUAGE OverloadedStrings #-}

module Main where

--import Data.Text hiding (head, tail, reverse, length, map)
import Control.Monad
import Text.CSV
import System.Directory
import System.Process
import System.Exit

--gpg = "/usr/local/bin/gpg2"
gpg = "/usr/bin/gpg2"

keyid = "01234567" -- !!!Fill in the real I.D. here!!!

data Entry = Entry { fCategory :: String
                   , fDescription :: String
                   , fWebsite :: String
                   , fUsername :: String
                   , fPassword :: String
                   , fNotes :: String
                   };

instance Show Entry where
  show e = fPassword e
        ++ nonempty "User" (fUsername e)
        ++ nonempty "Website" (fWebsite e)
        ++ nonempty "Notes" (fNotes e)
    where
      nonempty :: String -> String -> String
      nonempty l v = if length v > 0 then "\n" ++ l ++ ": " ++ v else ""

pathOf e = (sanitize (fCategory e), sanitize (fDescription e))

sanitize = map substsafe
  where
  substsafe '/' = '-'
  substsafe '*' = '+'
  substsafe x   = x

record2entry :: Record -> Maybe Entry
record2entry [fCat,fDesc,fWeb,fUser,fPass,fNote,_] =
  Just (Entry { fCategory = fCat
              , fDescription = fDesc
              , fWebsite = fWeb
              , fUsername = fUser
              , fPassword = fPass
              , fNotes = fNote
              })
record2entry _ = Nothing

main = parseCSVFromFile "oisafe.csv"
       >>= either (error . show) ((mapM_ makeEntry) . tail)

makeEntry :: Record -> IO ()
makeEntry = buildFile . record2entry

buildFile :: Maybe Entry -> IO ()
buildFile Nothing = return ()
buildFile (Just e) = do
  let 
    (sub, file) = pathOf e
    dir = "password-store/" ++ sub
    path = dir ++ "/" ++ file ++ ".gpg"
    cont = show e
  (rc, stdout, stderr) <- readProcessWithExitCode gpg ["-ae", "-r", keyid] cont
  when (rc /= ExitSuccess) $ error $ "gpg rc " ++ (show rc) ++ " message " ++ stderr
  createDirectoryIfMissing True dir
  writeFile path stdout
