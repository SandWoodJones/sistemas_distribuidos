{
  pkgs ? import <nixpkgs> { },
}:
let
  jdk = pkgs.jdk21;
in
pkgs.mkShell {
  packages = with pkgs; [
    jdk
    (maven.override { jdk_headless = jdk; })
    jdt-language-server
  ];

  JAVA_HOME = "${jdk}";
}
