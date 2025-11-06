// /Users/tuanpham/Chinese-chess-nework-programming/src/Front_end/components/cc-next-button.js
class CCNextButton extends HTMLElement{
	static get observedAttributes(){ return ['src']; }
	constructor(){
		super();
		const root = this.attachShadow({mode:'open'});
		const uniqueId = Math.random().toString(36).substr(2,9);
		root.innerHTML = `
			<style>
				:host{
					display:block;
					position: relative;
					width: calc(var(--w, 80px) * var(--scale));
					height: calc(var(--w, 80px) * var(--scale));
					flex-shrink: 0;
				}
				.wrap{
					position: relative;
					display: inline-block;
					width: 100%;
					height: 100%;
					cursor: pointer;
					transition: transform .2s ease;
				}
				.wrap:hover{ transform: translateY(-2px) scale(1.05); }
				.button-img{
					width: 100%;
					height: 100%;
					display: block;
					object-fit: contain;
				}
				svg.ring{
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
				<img class="button-img" id="buttonImg" part="button" />
				<svg class="ring" preserveAspectRatio="xMidYMid meet" aria-hidden="true">
					<defs>
						<linearGradient id="cc-next-grad-${uniqueId}" x1="0" y1="0" x2="1" y2="1">
							<stop offset="0%" stop-color="rgba(255,243,174,.95)"/>
							<stop offset="100%" stop-color="rgba(255,215,64,.85)"/>
						</linearGradient>
					</defs>
					<circle cx="50%" cy="50%" r="calc(50% - calc(var(--stroke-offset, -8px) * var(--scale)))" 
						fill="none" 
						stroke="url(#cc-next-grad-${uniqueId})" 
						stroke-width="calc(4px * var(--scale))"/>
				</svg>
			</div>
		`;
		this.$wrap = root.querySelector('.wrap');
		this.$img = root.querySelector('#buttonImg');
	}
	connectedCallback(){
		this.#sync();
		this.$wrap.addEventListener('click', this.#handleClick);
	}
	disconnectedCallback(){
		this.$wrap.removeEventListener('click', this.#handleClick);
	}
	attributeChangedCallback(){ this.#sync(); }
	#sync(){
		const src = this.getAttribute('src') || './assets/next-button.png';
		if(this.$img) this.$img.src = src;
	}
	#handleClick = (e) => {
		this.dispatchEvent(new CustomEvent('cc-next-click', {
			bubbles: true,
			detail: { button: this }
		}));
	}
}
customElements.define('cc-next-button', CCNextButton);