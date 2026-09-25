# LVGL Fonts

- [Online Converter](https://lvgl.io/tools/fontconverter)
- [Offline Converter](https://github.com/lvgl/lv_font_conv)
- [Google Fonts](https://fonts.google.com)

## Install

```sh
# install npm
sudo apt install npm
mkdir -p ~/.npm-global
npm config set prefix '~/.npm-global'
# add to shell profile
export PATH="$HOME/.npm-global/bin:$PATH"

# install lv_font_conv
npm i -g lv_font_conv
```

## Convert

To add a font, add the `*.ttf` file to this folder, and add it's sizes and name to `fonts.csv`.

To generate all fonts, run:

```sh
./scripts/mkfonts.sh
```
