if [[ -z "${JAVA_HOME}" ]]; then
  export JAVA_HOME="$HOME/.jdks/openjdk-16.0.2"
fi

gnome-terminal -- "$JAVA_HOME"/bin/java -jar ../out/artifacts/PermissionBased_jar/PermissionBased.jar -a 127.0.0.1 -p 20000 -n 5 -i 0 &
gnome-terminal -- "$JAVA_HOME"/bin/java -jar ../out/artifacts/PermissionBased_jar/PermissionBased.jar -a 127.0.0.1 -p 20000 -n 5 -i 5 &
gnome-terminal -- "$JAVA_HOME"/bin/java -jar ../out/artifacts/PermissionBased_jar/PermissionBased.jar -a 127.0.0.1 -p 20000 -n 5 -i 15 &
gnome-terminal -- "$JAVA_HOME"/bin/java -jar ../out/artifacts/PermissionBased_jar/PermissionBased.jar -a 127.0.0.1 -p 20000 -n 5 -i 10 &
gnome-terminal -- "$JAVA_HOME"/bin/java -jar ../out/artifacts/PermissionBased_jar/PermissionBased.jar -a 127.0.0.1 -p 20000 -n 5 -i 20 &
