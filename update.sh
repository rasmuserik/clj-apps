for REPOS in `cat assets/repos.lst`
do
  DIR=`echo $REPOS | sed -e s/'.*\\/'//`
  echo $REPOS $DIR
  if [ -d $DIR ]; then
    cd $DIR
    git pull
    cd ..
  else
    git clone git@github.com:$REPOS || git clone https://github.com/$REPOS".git" < /dev/zero
  fi
  echo /$DIR/ >> .gitignore
  cat .gitignore | sort | uniq > .gitignore.new
  mv .gitignore.new .gitignore
done
