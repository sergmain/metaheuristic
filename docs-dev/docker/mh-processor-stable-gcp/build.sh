#set -ex
# SET THE FOLLOWING VARIABLES
# docker hub username
USERNAME=metaheuristic
# image name
IMAGE=mh-processor

# ensure we're up to date
git pull

# bump version
#docker run --rm -v "$PWD":/app treeder/bump patch
version=`$(date -I)-$(date +%s | grep -o '.....$')`
echo "version: $version"

# run build
docker build -t $USERNAME/$IMAGE:latest .

# tag it
git add -A
git commit -m "version $version"
git tag -a "$version" -m "version $version"
git push
git push --tags
docker tag $USERNAME/$IMAGE:latest $USERNAME/$IMAGE:$version

# push it
docker push $USERNAME/$IMAGE:latest
docker push $USERNAME/$IMAGE:$version

