setup-deps:
	cd .. && git clone git@github.com:zcaudate/foundation-base.git

setup-deps-pull:
	cd foundation-base && git pull

setup-checkouts:
	mkdir -p checkouts
	cd checkouts && ln -s ../../foundation-base  foundation-base
