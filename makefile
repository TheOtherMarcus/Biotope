
SRCFILES = $(shell find src -type f | grep -v "\~" | grep -v "\.")
MARKFILES = $(patsubst %, %.m, $(SRCFILES))

$(info $$SRCFILES is [${SRCFILES}])
$(info $$MARKFILES is [${MARKFILES}])

.PHONY: all
all: $(MARKFILES)
	./logreader.sh

$(MARKFILES): %.m: %
	python boot.py $(notdir $(<D)) $(<F) $<
	touch $(@)
