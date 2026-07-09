{
  description = "ViTune development tooling";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";
    flake-parts.url = "github:hercules-ci/flake-parts";
    treefmt-nix = {
      url = "github:numtide/treefmt-nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    inputs@{ flake-parts, ... }:
    flake-parts.lib.mkFlake { inherit inputs; } {
      systems = [
        "x86_64-linux"
        "aarch64-linux"
        "aarch64-darwin"
      ];

      imports = [
        inputs.treefmt-nix.flakeModule
      ];

      perSystem =
        { pkgs, system, ... }:

        {
          _module.args.pkgs = import inputs.nixpkgs {
            inherit system;
            allowUnfree = true;
            overlays = [
              (final: _prev: {
                jdk = final.openjdk25;
                gradle = final.gradle_9;
                python = final.python314;
              })
            ];
          };
          treefmt = {
            programs.nixfmt.enable = true;
            programs.keep-sorted.enable = true;
            programs.ruff-format.enable = true;
          };

          devShells.default = pkgs.mkShell {
            packages = with pkgs; [
              jdk
              gradle
              python
            ];

            env = {
              JAVA_HOME = pkgs.jdk.home;
              GRADLE_JAVA_HOME = pkgs.jdk.home;
            };
          };
        };
    };
}
