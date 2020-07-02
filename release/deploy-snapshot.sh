#!/usr/bin/env sh

export SSHDIR="$HOME/.ssh"
mkdir -p "$SSHDIR"
echo "$ACTIONS_DEPLOY_KEY" > "$SSHDIR/key"
chmod 600 "$SSHDIR/key"
export SERVER_DEPLOY_STRING="$SSH_USERNAME@$SERVER_ADDRESS:$SERVER_DESTINATION"
mkdir -p "$GITHUB_WORKSPACE/APS"
cp -v "$GITHUB_WORKSPACE/app/build/outputs/apk/free/release/*.apk" "$GITHUB_WORKSPACE/APS"
cp -v "$GITHUB_WORKSPACE/app/build/outputs/apk/nonFree/release/*.apk" "$GITHUB_WORKSPACE/APS"
cd "$GITHUB_WORKSPACE/APS"
rsync -ahvcr --omit-dir-times --progress --delete --no-o --no-g -e "ssh -i $SSHDIR/key -o StrictHostKeyChecking=no -p $SSH_PORT" . "$SERVER_DEPLOY_STRING" || exit 1
exit 0
