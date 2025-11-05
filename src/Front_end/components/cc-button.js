// /Users/tuanpham/Chinese-chess-nework-programming/src/Front_end/components/cc-button.js
class CCButton extends HTMLElement{
	static get observedAttributes(){ return ['text','src','alt']; }
	constructor(){
		super();
		const root = this.attachShadow({mode:'open'});
		root.innerHTML = `
			<style>
				:host{
					display:block;
					position: absolute;
					top: calc(var(--top, 0px) * var(--scale));
					left: calc(var(--left, 0px) * var(--scale));
					width: calc(var(--w, 200px) * var(--scale));
					cursor: pointer;
				}
				.wrap{
					position: relative;
					display: inline-block;
					transition: transform .3s ease;
				}
				.wrap:hover{ transform: translateY(-3px); }

				img{
					display:block;
					width:100%;
					height:auto;
					position: relative;
					z-index: 1;
				}

				svg{
					position:absolute;
					inset: calc(var(--stroke-offset, -8px) * var(--scale));
					width: calc(100% + (var(--stroke-offset, -8px) * 2 * var(--scale)));
					height: calc(100% + (var(--stroke-offset, -8px) * 2 * var(--scale)));
					pointer-events:none;
					z-index: 2;
					overflow: visible;
				}
				.ring{ opacity:0; transition: opacity .25s ease; }
				.wrap:hover .ring{ opacity:1; }
			</style>

			<div class="wrap" part="container">
				<img class="cc-img" part="img" />
				<svg class="ring" preserveAspectRatio="xMidYMid meet" aria-hidden="true">
					<defs>
						<linearGradient id="cc-button-grad-${Math.random().toString(36).substr(2,9)}" x1="0" y1="0" x2="1" y2="1">
							<stop offset="0%" stop-color="rgba(255,243,174,.95)"/>
							<stop offset="100%" stop-color="rgba(255,215,64,.85)"/>
						</linearGradient>
						<filter id="cc-button-filter-${Math.random().toString(36).substr(2,9)}"
							filterUnits="objectBoundingBox"
							x="-0.2" y="-0.2" width="1.4" height="1.4">
							<feColorMatrix in="SourceGraphic" type="matrix"
								values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 1 0" result="alpha"/>
							<feMorphology in="alpha" operator="dilate" radius="8" result="dilated"/>
							<feComposite in="dilated" in2="alpha" operator="out" result="ring"/>
						</filter>
						<mask id="cc-button-mask-${Math.random().toString(36).substr(2,9)}">
							<image id="maskImg" href="" x="0" y="0" width="100%" height="100%"
								preserveAspectRatio="xMidYMid meet" filter="url(#cc-button-filter-${Math.random().toString(36).substr(2,9)})"/>
						</mask>
					</defs>
					<rect x="-10%" y="-10%" width="120%" height="120%"
						fill="url(#cc-button-grad-${Math.random().toString(36).substr(2,9)})" 
						mask="url(#cc-button-mask-${Math.random().toString(36).substr(2,9)})"/>
				</svg>
			</div>
		`;
		this.$img = root.querySelector('img');
		this.$maskImg = root.querySelector('#maskImg');
		this.gradId = root.querySelector('linearGradient').id;
		this.filterId = root.querySelector('filter').id;
		this.maskId = root.querySelector('mask').id;
	}
	connectedCallback(){ 
		this.#sync();
		this.addEventListener('click', this.#handleClick);
	}
	disconnectedCallback(){
		this.removeEventListener('click', this.#handleClick);
	}
	attributeChangedCallback(){ this.#sync(); }
	#sync(){
		if(this.$img){
			const src = this.getAttribute('src') || '';
			this.$img.src = src;
			this.$img.alt = this.getAttribute('alt') || '';
			if(this.$maskImg) this.$maskImg.setAttribute('href', src);
		}
	}
	#handleClick = (e) => {
		this.dispatchEvent(new CustomEvent('cc-button-click', {
			bubbles: true,
			detail: { text: this.getAttribute('text') || '' }
		}));
	}
}
customElements.define('cc-button', CCButton);